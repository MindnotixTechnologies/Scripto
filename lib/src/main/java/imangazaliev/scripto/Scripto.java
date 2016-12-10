package imangazaliev.scripto;

import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

import imangazaliev.scripto.converter.JavaConverter;
import imangazaliev.scripto.converter.JavaScriptConverter;
import imangazaliev.scripto.js.ScriptoProxy;
import imangazaliev.scripto.java.JavaInterface;
import imangazaliev.scripto.java.JavaInterfaceConfig;
import imangazaliev.scripto.utils.ScriptoAssetsJavaScriptReader;
import imangazaliev.scripto.utils.ScriptoUtils;

/**
 * Создает прокси для JS-скриптов. Добавляет JavaScript интерфейсы
 */
public class Scripto {

    public interface ErrorHandler {
        void onError(ScriptoException error);
    }

    private WebView webView;
    private JavaScriptConverter javaScriptConverter;
    private JavaConverter javaConverter;

    private ErrorHandler errorHandler;
    private ScriptoPrepareListener prepareListener;

    private ScriptoAssetsJavaScriptReader scriptoAssetsJavaScriptReader;
    private ArrayList<String> jsScripts;

    private Scripto(Builder builder) {
        this.webView = builder.webView;
        this.javaScriptConverter = builder.javaScriptConverter;
        this.javaConverter = builder.javaConverter;

        jsScripts = new ArrayList<>();
        scriptoAssetsJavaScriptReader = new ScriptoAssetsJavaScriptReader(webView.getContext());

        initWebView(builder);
    }

    private void initWebView(Builder builder) {
        ScriptoWebViewClient scriptoWebViewClient = builder.scriptoWebViewClient;
        scriptoWebViewClient.setOnPageLoadedListener(new ScriptoWebViewClient.OnPageLoadedListener() {
            @Override
            public void onPageLoaded() {
                addJsScripts();
            }
        });
        webView.setWebViewClient(scriptoWebViewClient);

        webView.getSettings().setJavaScriptEnabled(true);
        //ждем команду от JS о готовности к работе
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onScriptoPrepared() {
                ScriptoUtils.runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        if (prepareListener != null) {
                            prepareListener.onScriptoPrepared();
                        }
                    }
                });
            }
        }, "ScriptoPreparedListener");
    }

    private void addJsScripts() {
        StringBuilder fullJsCodeBuilder = new StringBuilder();

        //собираем весь код в одну строку
        for (int i = jsScripts.size() - 1; i >= 0; i--) {
            fullJsCodeBuilder.append(jsScripts.get(i));
        }

        //оповещаем java-библиотеку, о готовности к работе
        fullJsCodeBuilder.append("ScriptoPreparedListener.onScriptoPrepared();");

        //вставляем весь код в блок head
        String encodedJsCode = Base64.encodeToString(fullJsCodeBuilder.toString().getBytes(), Base64.NO_WRAP);
        webView.loadUrl("javascript:(function() {" +
                "   var head = document.getElementsByTagName('head').item(0);" +
                "   var script = document.createElement('script');" +
                "   script.type = 'text/javascript';" +
                // Tell the browser to BASE64-decode the string into your script !!!
                "   script.innerHTML = window.atob('" + encodedJsCode + "');" +
                "   head.insertBefore(script, head.firstChild);" +
                "})();");
    }

    public void addJsScriptFromAssets(String filePath) {
        String jsCode = scriptoAssetsJavaScriptReader.read(filePath);
        if (jsCode == null) {
            ScriptoException jsFileReadError = new ScriptoException(String.format("File %s in assets folder not found", filePath));
            if (errorHandler != null) {
                errorHandler.onError(jsFileReadError);
            } else {
                throw jsFileReadError;
            }
        } else {
            addJsScript(jsCode);
        }
    }

    public void addJsScript(String jsCode) {
        jsScripts.add(jsCode);
    }

    /**
     * Создает прокси из интерфейс для вызова JS-функций
     */
    public <T> T create(final Class<T> script) {
        ScriptoUtils.checkNotNull(script, "Script class can not be null");

        //если объект не является интерфейсом, выбрасываем исключение
        ScriptoUtils.validateScriptInterface(script);
        return (T) Proxy.newProxyInstance(script.getClassLoader(), new Class<?>[]{script}, new ScriptoProxy(this, script));
    }

    public WebView getWebView() {
        return webView;
    }

    public JavaScriptConverter getJavaScriptConverter() {
        return javaScriptConverter;
    }

    public JavaConverter getJavaConverter() {
        return javaConverter;
    }

    public void addInterface(String tag, Object jsInterface) {
        addInterface(tag, jsInterface, new JavaInterfaceConfig());
    }

    public void addInterface(String tag, Object jsInterface, JavaInterfaceConfig config) {
        if (tag == null) {
            throw new NullPointerException("Tag object can't be null");
        }

        if (jsInterface == null) {
            throw new NullPointerException("Tag can't be null");
        }

        if (config == null) {
            throw new NullPointerException("Config object can't be null");
        }

        webView.addJavascriptInterface(new JavaInterface(this, tag, jsInterface, config), tag);
    }

    public void removeInterface(String tag) {
        webView.removeJavascriptInterface(tag);
    }

    public void onError(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void onPrepared(ScriptoPrepareListener prepareListener) {
        this.prepareListener = prepareListener;
    }

    public static class Builder {

        private WebView webView;
        private ScriptoWebViewClient scriptoWebViewClient;
        private JavaScriptConverter javaScriptConverter;
        private JavaConverter javaConverter;

        public Builder(WebView webView) {
            this.webView = webView;

            this.scriptoWebViewClient = new ScriptoWebViewClient();
            this.javaScriptConverter = new JavaScriptConverter();
            this.javaConverter = new JavaConverter();
        }

        public Builder setWebViewClient(ScriptoWebViewClient scriptoWebViewClient) {
            ScriptoUtils.checkNotNull(scriptoWebViewClient, "ScriptoWebViewClient can not be null");
            this.scriptoWebViewClient = scriptoWebViewClient;
            return this;
        }

        public Builder setJavaScriptConverter(JavaScriptConverter javaScriptConverter) {
            ScriptoUtils.checkNotNull(javaScriptConverter, "Converter can not be null");
            this.javaScriptConverter = javaScriptConverter;
            return this;
        }

        public Builder setJavaConverter(JavaConverter javaConverter) {
            ScriptoUtils.checkNotNull(javaConverter, "Converter can not be null");
            this.javaConverter = javaConverter;
            return this;
        }

        public Scripto build() {
            return new Scripto(this);
        }

    }


}