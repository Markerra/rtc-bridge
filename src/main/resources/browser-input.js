(() => {
    if (window.__browserInputLoaded) return;

    if (window.top !== window) return;

    window.__browserInputLoaded = true;

    function log(level, message) {
        if (window.bridgeLog) {
            window.bridgeLog(level, "[INPUT] " + message);
        } else {
            console.log("[INPUT]", message);
        }
    }

    const config = window.__bridgeConfig;

    if (!config) {
        log("ERROR", "Missing config");

        return;
    }

    const GAME_URL = `ws://${config.bridge.host}:${config.bridge.port}${config.bridge.gameEndpoint}`;

    log("INFO", "Connecting " + GAME_URL);

    // ==========================
    // Audio settings
    // ==========================

    const AUDIO = config.audio.format;

    const sampleRate = AUDIO.sampleRate;

    const frameSamples = (sampleRate * AUDIO.frameDurationMs) / 1000;

    // ==========================
    // WebSocket
    // ==========================

    let socket;

    function connectSocket() {
        socket = new WebSocket(GAME_URL);

        socket.binaryType = "arraybuffer";

        socket.onopen = () => {
            log("INFO", "Input socket connected");

            socket.send(
                JSON.stringify({
                    type: "hello",

                    role: "consumer",
                }),
            );
        };

        socket.onmessage = (event) => {
            if (event.data instanceof ArrayBuffer) {
                pushPCM(event.data);
            } else if (typeof event.data === "string") {
                // Ловим текстовые сообщения (включая ошибки от сервера)
                try {
                    const msg = JSON.parse(event.data);
                    if (msg.type === "error" || msg.message) {
                        log("ERROR", `Server rejected packet: ${msg.message}`);
                    }
                } catch (e) {
                    log("DEBUG", `Text msg: ${event.data}`);
                }
            }
        };

        socket.onclose = () => {
            log("WARN", "Socket closed");

            setTimeout(connectSocket, 2000);
        };
    }

    connectSocket();

    // ==========================
    // PCM queue
    // ==========================

    const pcmQueue = [];

    // ==========================
    // PCM queue & Debug
    // ==========================

    let packetsReceived = 0;
    let lastLogTime = Date.now();

    function pushPCM(buffer) {
        packetsReceived++;
        const samples = new Int16Array(buffer);
        const float = new Float32Array(samples.length);

        const VOLUME_MULT = 1.1;

        let maxAmplitude = 0;

        // Конвертация в Float32 и поиск максимальной громкости в пакете
        for (let i = 0; i < samples.length; i++) {
            float[i] = (samples[i] / 32768) * VOLUME_MULT;
            let abs = Math.abs(float[i]);
            if (abs > maxAmplitude) {
                maxAmplitude = abs;
            }
        }

        // Выводим статистику раз в секунду
        const now = Date.now();
        if (now - lastLogTime >= 1000) {
            log("DEBUG", `[Audio In] Packets/sec: ${packetsReceived} | Max Volume: ${maxAmplitude.toFixed(4)}`);
            packetsReceived = 0;
            lastLogTime = now;
        }

        if (window.pcmProcessor) window.pcmProcessor.port.postMessage(float);
    }

    // ==========================
    // Fake microphone & AudioWorklet
    // ==========================

    let fakeStream = null;
    let audioContext = null;
    let destination = null;
    let workletURL = null;

    async function createFakeMicrophone() {

        if (!audioContext || audioContext.state === "closed") {

            audioContext = new AudioContext({
                sampleRate: 48000
            });

            await audioContext.resume();

            const workletCode = `
        class PCMProcessor extends AudioWorkletProcessor {

            constructor() {
                super();

                this.buffer = new Float32Array(96000);
                this.readIndex = 0;
                this.writeIndex = 0;

                this.port.onmessage = e => {
                    const data = e.data;

                    for (let i = 0; i < data.length; i++) {
                        this.buffer[this.writeIndex] = data[i];
                        this.writeIndex =
                          (this.writeIndex + 1) % this.buffer.length;
                    }
                };
            }


            process(inputs, outputs) {

                const output = outputs[0][0];

                for(let i = 0; i < output.length; i++) {

                    if(this.readIndex !== this.writeIndex) {
                        output[i] =
                          this.buffer[this.readIndex];

                        this.readIndex =
                          (this.readIndex + 1)
                          % this.buffer.length;

                    } else {
                        output[i] = 0;
                    }
                }

                return true;
            }
        }

        registerProcessor(
          'pcm-input-processor',
          PCMProcessor
        );
        `;


            const blob = new Blob(
                [workletCode],
                {type:"application/javascript"}
            );

            await audioContext.audioWorklet.addModule(
                URL.createObjectURL(blob)
            );
        }


        if (audioContext.state === "suspended") {
            await audioContext.resume();
        }


        // ВАЖНО: новый destination каждый раз

        const destination =
            audioContext.createMediaStreamDestination();


        const processor =
            new AudioWorkletNode(
                audioContext,
                "pcm-input-processor"
            );


        processor.connect(destination);


        window.pcmProcessor = processor;


        return destination.stream;
    }

    // ==========================
    // Override getUserMedia
    // ==========================

    const originalGetUserMedia = navigator.mediaDevices.getUserMedia.bind(
        navigator.mediaDevices,
    );

    navigator.mediaDevices.getUserMedia = async function (constraints) {
        const stream = await originalGetUserMedia(constraints);

        log("INFO", "Original microphone granted");

        if (constraints && constraints.audio) {
            const fake = await createFakeMicrophone();

            const fakeTrack = fake.getAudioTracks()[0];

            log("DEBUG",
                `Fake track state: ${fakeTrack.readyState}, enabled: ${fakeTrack.enabled}`
            );

            const oldTrack = stream.getAudioTracks()[0];

            if (oldTrack) {
                stream.removeTrack(oldTrack);

                oldTrack.stop();
            }

            stream.addTrack(fakeTrack);

            log("INFO", "Microphone track replaced");
        }

        return stream;
    };

    log("INFO", "Browser input bridge initialized");
})();
