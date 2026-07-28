(() => {
    if (window.__pcmBridgeLoaded) return;
    if (window.top !== window) return;
    window.__pcmBridgeLoaded = true;

    // ==========================
    // Logging
    // ==========================
    function log(level, message) {
        const text = `[PCM] ${message}`;
        if (window.bridgeLog) {
            window.bridgeLog(level, text);
        } else {
            console.log(text);
        }
    }

    // ==========================
    // Config
    // ==========================
    const config = window.__bridgeConfig;
    if (!config) {
        log("ERROR", "Missing __bridgeConfig");
        return;
    }

    const AUDIO = config.audio.format;
    const FRAME_SAMPLES = (AUDIO.sampleRate * AUDIO.frameDurationMs) / 1000; // 960 для 20мс при 48kHz

    log("DEBUG", `Audio format ${AUDIO.sampleRate}Hz ${AUDIO.channels}ch ${AUDIO.bitsPerSample}bit. Using AudioWorklet!`);

    // ==========================
    // WebSocket
    // ==========================
    class BridgeSocket {
        constructor(url) {
            this.url = url;
            this.socket = null;
            this.ready = false;
            this.connect();
        }

        connect() {
            log("DEBUG", `Connecting ${this.url}`);
            this.socket = new WebSocket(this.url);
            this.socket.binaryType = "arraybuffer";

            this.socket.onopen = () => {
                log("INFO", "WebSocket connected");
                this.socket.send(JSON.stringify({ type: "hello", role: "source" }));
            };

            this.socket.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    if (msg.type === "state" && msg.state === "ready") {
                        this.ready = true;
                        log("INFO", "Bridge ready");
                    }
                } catch {}
            };

            this.socket.onerror = () => log("ERROR", "WebSocket error");
            this.socket.onclose = () => {
                log("WARN", "WebSocket closed");
                this.ready = false;
                setTimeout(() => this.connect(), 2000);
            };
        }

        send(buffer) {
            if (!this.ready || !this.socket || this.socket.readyState !== WebSocket.OPEN) return;
            this.socket.send(buffer);
        }
    }

    const bridgeSocket = new BridgeSocket(
        `ws://${config.bridge.host}:${config.bridge.port}${config.bridge.browserEndpoint}`
    );

    // ==========================
    // Metrics & Logging
    // ==========================
    let framesSent = 0;
    let callbacks = 0;
    let samplesReceived = 0;

    setInterval(() => {
        let state = window.__pcmContext ? window.__pcmContext.state : "unknown";
        let trackState = window.__pcmTrack ? window.__pcmTrack.readyState : "none";
        let trackMuted = window.__pcmTrack ? window.__pcmTrack.muted : false;

        log(
            "DEBUG",
            `worklet_blocks=${callbacks} ` +
            `samples=${samplesReceived} ` +
            `frames_sent=${framesSent} ` +
            `ctx=${state} ` +
            `track=${trackState} muted=${trackMuted}`
        );

        callbacks = 0;
        samplesReceived = 0;
        framesSent = 0;
    }, 1000);

    // ==========================
    // AudioWorklet Generator (Blob)
    // ==========================
    // Создаем код Worklet-процессора, который будет жить в изолированном аудио-потоке
    const workletCode = `
        class PcmProcessor extends AudioWorkletProcessor {
            constructor() {
                super();
                this.targetSamples = ${FRAME_SAMPLES}; // 960 сэмплов (20мс)
                this.buffer = new Float32Array(this.targetSamples);
                this.offset = 0;
            }

            process(inputs, outputs, parameters) {
                const input = inputs[0];
                if (!input || !input[0]) return true;

                const channelData = input[0]; // Моно-канал (128 сэмплов за квант времени)
                let i = 0;

                while (i < channelData.length) {
                    const needed = this.targetSamples - this.offset;
                    const available = channelData.length - i;
                    const toCopy = Math.min(needed, available);

                    this.buffer.set(channelData.subarray(i, i + toCopy), this.offset);
                    this.offset += toCopy;
                    i += toCopy;

                    // Как только накопили ровно 20мс (960 сэмплов) — конвертируем и шлем на главный поток
                    if (this.offset >= this.targetSamples) {
                        const int16 = new Int16Array(this.targetSamples);
                        for (let j = 0; j < this.targetSamples; j++) {
                            let s = Math.max(-1, Math.min(1, this.buffer[j]));
                            int16[j] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                        }
                        
                        // Отправляем готовый PCM-кадр (Transferable ArrayBuffer для нулевой задержки)
                        this.port.postMessage(int16.buffer, [int16.buffer]);
                        this.offset = 0;
                    }
                }
                return true; // Держим процессор активным
            }
        }
        registerProcessor('pcm-processor', PcmProcessor);
    `;

    // ==========================
    // Audio Capture (Async AudioWorklet)
    // ==========================
    const attachedStreams = new WeakSet();

    async function attachStream(stream) {
        if (!stream || attachedStreams.has(stream)) return;
        const tracks = stream.getAudioTracks();
        if (tracks.length === 0) return;

        attachedStreams.add(stream);
        log("INFO", `Attach stream ${stream.id} using AudioWorklet`);

        const context = new (window.AudioContext || window.webkitAudioContext)({
            sampleRate: AUDIO.sampleRate,
        });

        window.__pcmContext = context;
        window.__pcmTrack = tracks[0];

        try {
            // 1. Создаем Blob URL для нашего Worklet-процессора и загружаем в аудио-движок
            const blob = new Blob([workletCode], { type: "application/javascript" });
            const workletUrl = URL.createObjectURL(blob);
            await context.audioWorklet.addModule(workletUrl);
            URL.revokeObjectURL(workletUrl); // Очищаем ссылку из памяти

            // 2. Создаем узлы аудио-графа
            const source = context.createMediaStreamSource(stream);
            const workletNode = new AudioWorkletNode(context, "pcm-processor");

            const mute = context.createGain();
            mute.gain.value = 0.001;

            // ==========================================
            // Oscillator Anchor
            // ==========================================
            const anchorOsc = context.createOscillator();
            anchorOsc.frequency.value = 1; // Любая частота
            anchorOsc.connect(mute);         // Пускаем в тот же тихий канал
            anchorOsc.start();               // Запускаем вечный двигатель
            // ==========================================

            // 3. Соединяем граф: Источник -> Worklet -> Gain(тишина) -> Динамики
            source.connect(workletNode);
            workletNode.connect(mute);
            mute.connect(context.destination);

            // 4. Принимаем готовые 16-битные кадры по 1920 байт напрямую из Worklet-потока
            workletNode.port.onmessage = (event) => {
                const pcmBuffer = event.data; // ArrayBuffer 1920 bytes

                callbacks++;
                samplesReceived += FRAME_SAMPLES;

                bridgeSocket.send(pcmBuffer);
                framesSent++;
            };

            tracks[0].onended = () => {
                log("INFO", "Audio track ended");
                source.disconnect();
                workletNode.disconnect();
                mute.disconnect();
                context.close();
            };

        } catch (e) {
            log("ERROR", `Failed to initialize AudioWorklet: ${e.message}`);
        }
    }

    // ==========================
    // Detect MediaStreams
    // ==========================
    const descriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, "srcObject");
    if (descriptor) {
        Object.defineProperty(HTMLMediaElement.prototype, "srcObject", {
            get() {
                return descriptor.get.call(this);
            },
            set(stream) {
                descriptor.set.call(this, stream);
                if (stream) {
                    this.muted = false;
                    this.volume = 0.002;
                    attachStream(stream);
                }
            },
        });
    }

    const observer = new MutationObserver(() => {
        document.querySelectorAll("audio,video").forEach((element) => {
            if (element.srcObject) attachStream(element.srcObject);
        });
    });

    observer.observe(document.documentElement, {
        childList: true,
        subtree: true,
    });

    log("INFO", "PCM bridge initialized with AudioWorklet Engine");
})();