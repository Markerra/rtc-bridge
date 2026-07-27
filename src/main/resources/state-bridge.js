(() => {
    if (window.top !== window)
        return;

    const visible = (element) => {
        try {
            return !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
        } catch (e) {
            return false;
        }
    };

    const hasCaptcha = () => {
        const frames = Array.from(document.querySelectorAll('iframe, div, embed'));
        const captchaExists = frames.some(el => {
            const str = `${el.className || ''} ${el.id || ''} ${el.src || ''}`.toLowerCase();
            return visible(el) && (str.includes('captcha') || str.includes('recaptcha') || str.includes('hcaptcha'));
        });

        if (captchaExists) return true;

        for (const frame of document.querySelectorAll('iframe')) {
            try {
                const doc = frame.contentDocument || frame.contentWindow?.document;
                if (doc && doc.body) {
                    const fText = (doc.body.innerText || '').toLowerCase();
                    if (fText.includes('решить каптчу') || fText.includes('я не робот') || fText.includes("i'm not a robot")) {
                        return true;
                    }
                }
            } catch (e) {}
        }
        return false;
    };

    const getAggregateText = () => {
        let fullText = document.body?.innerText || '';
        document.querySelectorAll('iframe').forEach(frame => {
            try {
                const doc = frame.contentDocument || frame.contentWindow?.document;
                if (doc && doc.body) {
                    fullText += ' ' + doc.body.innerText;
                }
            } catch (e) {}
        });
        return fullText.toLowerCase().replace(/\s+/g, ' ');
    };

    const detect = () => {
        if (hasCaptcha()) {
            return 'CAPTCHA_REQUIRED';
        }

        const text = getAggregateText();

        if (
            text.includes('ищем свободного') ||
            text.includes('идет поиск') ||
            text.includes('ожидание собеседника')
        ) {
            return 'SEARCHING';
        }

        if (
            text.includes('разговор с nekto')
        ) {
            return 'CONNECTED';
        }

        return 'PAGE_READY';
    };

    const report = () => {
        try {
            const currentState = detect();
            const savedState = sessionStorage.getItem('__rtc_bridge_state') || '';

            if (currentState !== savedState) {
                console.log(`[Bridge State] ${savedState || 'NONE'} -> ${currentState}`);

                sessionStorage.setItem('__rtc_bridge_state', currentState);

                if (typeof window.reportStateToJava === 'function') {
                    window.reportStateToJava(currentState).catch(err => {
                        console.error('[Bridge Send Error]', err);
                    });
                }
            }
        } catch (err) {
            console.error('[Bridge Error]', err);
        }
    };

    const begin = () => {
        if (window.__rtcInterval) clearInterval(window.__rtcInterval);

        new MutationObserver(report).observe(document.documentElement, {
            childList: true, subtree: true, characterData: true, attributes: true
        });

        window.__rtcInterval = setInterval(report, 400);
        report();
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', begin, { once: true });
    } else {
        begin();
    }
})();