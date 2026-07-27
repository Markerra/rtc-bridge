(() => {

    if (window.__pcmBridgeLoaded)
        return;

    if (window.top !== window)
        return;

    window.__pcmBridgeLoaded = true;


    // ==========================
    // Logging
    // ==========================

    function log(level, message) {

        const text =
            `[PCM] ${message}`;

        if (window.bridgeLog) {
            window.bridgeLog(
                level,
                text
            );
        } else {
            console.log(text);
        }
    }


    // ==========================
    // Config
    // ==========================

    const config =
        window.__bridgeConfig;


    if (!config) {

        log(
            "ERROR",
            "Missing __bridgeConfig"
        );

        return;
    }

    const MUTE_OUTPUT =
        config.browser.muteOutput ?? true;


    const AUDIO =
        config.audio.format;


    const FRAME_SAMPLES =
        AUDIO.sampleRate *
        AUDIO.frameDurationMs /
        1000;


    log(
        "DEBUG",
        `Audio format ${AUDIO.sampleRate}Hz ${AUDIO.channels}ch ${AUDIO.bitsPerSample}bit`
    );


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

            log(
                "DEBUG",
                `Connecting ${this.url}`
            );


            this.socket =
                new WebSocket(this.url);


            this.socket.binaryType =
                "arraybuffer";


            this.socket.onopen = () => {

                log(
                    "INFO",
                    "WebSocket connected"
                );


                this.socket.send(
                    JSON.stringify({

                        type: "hello",

                        role: "source"

                    })
                );

            };


            this.socket.onmessage = event => {

                try {

                    const msg =
                        JSON.parse(event.data);


                    if (
                        msg.type === "state"
                        &&
                        msg.state === "ready"
                    ) {

                        this.ready = true;


                        log(
                            "INFO",
                            "Bridge ready"
                        );

                    }


                } catch {

                }

            };


            this.socket.onerror = () => {

                log(
                    "ERROR",
                    "WebSocket error"
                );

            };


            this.socket.onclose = () => {

                log(
                    "WARN",
                    "WebSocket closed"
                );


                this.ready = false;


                setTimeout(
                    () => this.connect(),
                    2000
                );

            };

        }


        send(buffer) {

            if (
                !this.ready ||
                !this.socket ||
                this.socket.readyState !== WebSocket.OPEN
            )
                return;


            this.socket.send(buffer);

        }

    }


    const bridgeSocket =
        new BridgeSocket(
            `ws://${config.bridge.host}:${config.bridge.port}${config.bridge.browserEndpoint}`
        );



    // ==========================
    // Ring Buffer
    // ==========================

    class RingBuffer {

        constructor(size) {

            this.buffer =
                new Float32Array(size);

            this.size =
                size;


            this.read =
                0;


            this.write =
                0;


            this.length =
                0;

        }



        push(data) {

            for (
                let i = 0;
                i < data.length;
                i++
            ) {

                if (this.length >= this.size) {

                    // overflow protection

                    this.read =
                        (this.read + 1)
                        %
                        this.size;

                    this.length--;

                }


                this.buffer[this.write] =
                    data[i];


                this.write =
                    (this.write + 1)
                    %
                    this.size;


                this.length++;

            }

        }



        available() {

            return this.length;

        }



        pop(size) {


            if (this.length < size)
                return null;


            const result =
                new Float32Array(size);



            for (
                let i = 0;
                i < size;
                i++
            ) {

                result[i] =
                    this.buffer[this.read];


                this.read =
                    (this.read + 1)
                    %
                    this.size;


                this.length--;

            }


            return result;

        }

    }



    const pcmBuffer =
        new RingBuffer(
            FRAME_SAMPLES * 10
        );



    let framesSent = 0;



    function sendFrames() {


        while (
            pcmBuffer.available()
            >=
            FRAME_SAMPLES
            ) {


            const frame =
                pcmBuffer.pop(
                    FRAME_SAMPLES
                );


            const pcm =
                new Int16Array(
                    FRAME_SAMPLES
                );



            for (
                let i = 0;
                i < frame.length;
                i++
            ) {

                let sample =
                    frame[i];


                sample =
                    Math.max(
                        -1,
                        Math.min(
                            1,
                            sample
                        )
                    );


                pcm[i] =
                    sample < 0
                        ?
                        sample * 32768
                        :
                        sample * 32767;

            }



            bridgeSocket.send(
                pcm.buffer
            );


            framesSent++;


        }

    }



    setInterval(() => {

        if (framesSent > 0) {

            log(
                "DEBUG",
                `PCM frames sent: ${framesSent}`
            );


            framesSent = 0;

        }

    }, 1000);



    // ==========================
    // Audio Capture
    // ==========================


    const attachedStreams =
        new WeakSet();



    function attachStream(stream) {


        if (
            !stream ||
            attachedStreams.has(stream)
        )
            return;



        const tracks =
            stream.getAudioTracks();


        if (
            tracks.length === 0
        )
            return;



        attachedStreams.add(stream);



        log(
            "INFO",
            `Attach stream ${stream.id}`
        );



        const context =
            new AudioContext({

                sampleRate:
                AUDIO.sampleRate

            });



        const source =
            context.createMediaStreamSource(
                stream
            );



        const processor =
            context.createScriptProcessor(
                2048,
                1,
                1
            );



        /*
            Важно:

            Мы не отправляем звук в колонки.
            GainNode с нулевой громкостью
            оставляет обработку активной,
            но убирает локальное воспроизведение.
        */


        const mute =
            context.createGain();


        mute.gain.value = 0;



        source.connect(
            processor
        );


        processor.connect(mute);

        mute.connect(context.destination);


        processor.onaudioprocess =
            event => {


                const samples =
                    event.inputBuffer
                        .getChannelData(0);



                pcmBuffer.push(
                    samples
                );


                sendFrames();

            };



        tracks[0].onended = () => {

            log(
                "INFO",
                "Audio track ended"
            );


            processor.disconnect();

            source.disconnect();

            mute.disconnect();

            context.close();

        };


    }




    // ==========================
    // Detect MediaStreams
    // ==========================


    const descriptor =
        Object.getOwnPropertyDescriptor(
            HTMLMediaElement.prototype,
            "srcObject"
        );



    if (descriptor) {


        Object.defineProperty(
            HTMLMediaElement.prototype,
            "srcObject",
            {


                get() {

                    return descriptor.get.call(this);

                },


                set(stream) {


                    descriptor.set.call(this, stream);

                    if(stream) {

                        this.muted = true;
                        this.volume = 0;

                        attachStream(stream);

                    }

                }

            }

        );

    }



    const observer =
        new MutationObserver(() => {


            document
                .querySelectorAll(
                    "audio,video"
                )
                .forEach(element => {


                    if(element.srcObject)

                        attachStream(
                            element.srcObject
                        );


                });


        });



    observer.observe(
        document.documentElement,
        {
            childList:true,
            subtree:true
        }
    );



    log(
        "INFO",
        "PCM bridge initialized"
    );


})();