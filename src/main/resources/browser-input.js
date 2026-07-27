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

    function pushPCM(buffer) {
        const samples = new Int16Array(buffer);

        const float = new Float32Array(samples.length);

        for (let i = 0; i < samples.length; i++) {
            float[i] = samples[i] / 32768;
        }

        if (window.pcmProcessor) window.pcmProcessor.port.postMessage(float);
    }

    // ==========================
    // Fake microphone
    // ==========================

    let fakeStream = null;

    let audioContext = null;

    let destination = null;

    let processor = null;

    async function createFakeMicrophone() {
        if (fakeStream) {
            log("INFO", "Using existing fake stream");

            return fakeStream;
        }

        audioContext = new AudioContext({
            sampleRate: 48000,
        });

        await audioContext.resume();

        destination = audioContext.createMediaStreamDestination();

        /*
              Временно тестовый звук.
              Потом сюда подключим AudioWorklet.
            */

        const oscillator = audioContext.createOscillator();

        oscillator.frequency.value = 440;

        oscillator.connect(destination);

        oscillator.start();

        fakeStream = destination.stream;

        log("INFO", "Fake microphone created");

        return fakeStream;
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
