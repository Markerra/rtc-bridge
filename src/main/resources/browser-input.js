(() => {

    if (window.__browserInputLoaded)
        return;

    window.__browserInputLoaded = true;


    function log(level, message) {

        if(window.bridgeLog) {

            window.bridgeLog(
                level,
                "[INPUT] " + message
            );

        } else {

            console.log(
                "[INPUT]",
                message
            );

        }

    }



    const config =
        window.__bridgeConfig;


    if(!config) {

        log(
            "ERROR",
            "Missing config"
        );

        return;

    }



    const GAME_URL =
        `ws://${config.bridge.host}:${config.bridge.port}${config.bridge.gameEndpoint}`;



    log(
        "INFO",
        "Connecting " + GAME_URL
    );



    // ==========================
    // Audio settings
    // ==========================


    const AUDIO =
        config.audio.format;



    const sampleRate =
        AUDIO.sampleRate;



    const frameSamples =
        sampleRate *
        AUDIO.frameDurationMs /
        1000;



    // ==========================
    // WebSocket
    // ==========================


    let socket;


    function connectSocket() {


        socket =
            new WebSocket(
                GAME_URL
            );


        socket.binaryType =
            "arraybuffer";



        socket.onopen = () => {


            log(
                "INFO",
                "Input socket connected"
            );


            socket.send(
                JSON.stringify({

                    type:"hello",

                    role:"consumer"

                })
            );


        };



        socket.onmessage = event => {


            if(event.data instanceof ArrayBuffer) {


                pushPCM(
                    event.data
                );


            }


        };



        socket.onclose = () => {


            log(
                "WARN",
                "Socket closed"
            );


            setTimeout(
                connectSocket,
                2000
            );


        };


    }



    connectSocket();



    // ==========================
    // PCM queue
    // ==========================


    const pcmQueue = [];



    function pushPCM(buffer) {


        const samples =
            new Int16Array(
                buffer
            );


        for(let i = 0; i < samples.length; i++) {


            pcmQueue.push(
                samples[i] / 32768
            );


        }


    }




    // ==========================
    // Fake microphone
    // ==========================


    let audioContext;

    let destination;



    function createFakeMicrophone() {


        audioContext =
            new AudioContext({

                sampleRate

            });



        const oscillator =
            audioContext.createOscillator();


        /*
          временно для проверки.
          Когда PCM пойдет из WebSocket,
          этот блок можно удалить.
        */


        destination =
            audioContext.createMediaStreamDestination();



        const gain =
            audioContext.createGain();


        gain.gain.value = 0;



        oscillator.connect(
            gain
        );


        gain.connect(
            destination
        );


        oscillator.start();



        return destination.stream;

    }




    // ==========================
    // Override getUserMedia
    // ==========================


    const originalGetUserMedia =
        navigator.mediaDevices.getUserMedia.bind(
            navigator.mediaDevices
        );



    navigator.mediaDevices.getUserMedia =
        async function(constraints) {


            if(
                constraints
                &&
                constraints.audio
            ) {


                log(
                    "INFO",
                    "Providing fake microphone"
                );


                if(!destination)

                    createFakeMicrophone();



                return destination.stream;


            }



            return originalGetUserMedia(
                constraints
            );


        };



    log(
        "INFO",
        "Browser input bridge initialized"
    );


})();