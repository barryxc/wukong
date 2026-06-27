package io.github.barryxc.wukong.hook.core

import android.app.Application
import android.net.Network
import android.os.Build
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.barryxc.wukong.hook.utils.Logger
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL

/**
 * hook 网络请求：将默认的 no_proxy（直连）改为 proxy 模式。
 *
 * 原理：JDK / Android 的 [java.net.HttpURLConnection]、OkHttp 等默认都会通过
 * [ProxySelector.select] 获取代理列表，未配置代理时返回 `[Proxy.NO_PROXY]` 走直连。
 * 这里在 `select` 返回后将结果替换为指定的 HTTP 代理，从而让目标应用的网络请求统一走代理。
 *
 * 代理地址由 UI 配置（[Constant.KEY_MOCK_PROXY]，格式 `host:port`），为空时不生效，保持原直连行为。
 */
object HookNetworkProxy : ApplicationHook {
    @Volatile
    private var installed = false

    private val hookedProxySelectorClasses = mutableSetOf<String>()

    override fun installWithApplication(
        application: Application, loadPackageParam: XC_LoadPackage.LoadPackageParam
    ) {
        install(loadPackageParam)
    }

    fun install(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (installed) {
            return
        }
        synchronized(this) {
            if (installed) {
                return
            }
            hookProxySelector(loadPackageParam)
            hookProxySelectorSetDefault()
            hookOpenConnection()
            hookNetworkOpenConnection()
            hookHttpConnect(loadPackageParam)
            installed = true
        }
    }

    /**
     * 强制代理 + 连接建立级日志。
     * 注意：[ProxySelector.select] 只在「新建到某主机的连接」时调用一次，
     * keep-alive 复用的请求不会再触发，因此它代表的是「连接级」日志。
     */
    private fun hookProxySelector(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val selectorClasses = linkedSetOf<Class<*>>()
        ProxySelector.getDefault()?.javaClass?.let { selectorClasses.add(it) }
        listOf(
            "java.net.ProxySelectorImpl",
            "sun.net.spi.DefaultProxySelector",
        ).forEach { name ->
            runCatching {
                XposedHelpers.findClass(name, loadPackageParam.classLoader)
            }.onSuccess { selectorClasses.add(it) }
        }

        if (selectorClasses.isEmpty()) {
            Logger.e("[Proxy#select] no default ProxySelector implementation found")
            return
        }

        selectorClasses.forEach { hookProxySelectorClass(it) }
    }

    private fun hookProxySelectorSetDefault() {
        runCatching {
            XposedHelpers.findAndHookMethod(
                ProxySelector::class.java,
                "setDefault",
                ProxySelector::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val selector = param.args.getOrNull(0) as? ProxySelector ?: return
                        hookProxySelectorClass(selector.javaClass)
                        Logger.i("[Proxy#select] hooked new default selector ${selector.javaClass.name}")
                    }
                }
            )
        }.onFailure {
            Logger.e("[Proxy#select] hook ProxySelector.setDefault failed: ${it.message}")
        }
    }

    private fun hookProxySelectorClass(proxySelectorClass: Class<*>) {
        synchronized(hookedProxySelectorClasses) {
            if (!hookedProxySelectorClasses.add(proxySelectorClass.name)) {
                return
            }
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                proxySelectorClass, "select", URI::class.java, object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val uri = param.args.getOrNull(0) as? URI
                        // 只对 http(s) 请求强制 HTTP 代理，避免影响 socket/ftp 等其它 scheme。
                        if (uri?.scheme?.startsWith("http") != true) {
                            return
                    }
                    val proxy = resolveProxy() ?: return
                    param.result = listOf(proxy)
                    Logger.i("[Proxy#select] ${safeUri(uri)} -> $proxy")
                }
            }
            )
            Logger.i("[Proxy#select] hooked ${proxySelectorClass.name}")
        }.onFailure {
            synchronized(hookedProxySelectorClasses) {
                hookedProxySelectorClasses.remove(proxySelectorClass.name)
            }
            Logger.e("[Proxy#select] hook ${proxySelectorClass.name} failed: ${it.message}")
        }
    }

    /**
     * 逐请求日志：每次 [URL.openConnection] 都会触发，不受连接池/keep-alive 影响，
     * 因此能完整看到目标应用发起的每一条网络请求。
     */
    private fun hookOpenConnection() {
        XposedHelpers.findAndHookMethod(URL::class.java, "openConnection", object : XC_MethodHook() {
            @Throws(Throwable::class)
            override fun beforeHookedMethod(param: MethodHookParam) {
                val url = param.thisObject as? URL ?: return
                if (url.protocol?.startsWith("http") != true) {
                    return
                }
                val proxy = resolveProxy()
                if (proxy == null) {
                    Logger.i("[Proxy#openConnection] ${safeUrl(url)} config empty/direct")
                    return
                }
                param.result = url.openConnection(proxy)
                Logger.i("[Proxy#openConnection] forced ${safeUrl(url)} via $proxy")
            }
        })

        runCatching {
            XposedHelpers.findAndHookMethod(
                URL::class.java, "openConnection", Proxy::class.java, object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.thisObject as? URL ?: return
                        if (url.protocol?.startsWith("http") != true) {
                            return
                        }
                        val proxy = resolveProxy()
                        if (proxy == null) {
                            Logger.i("[Proxy#openConnection] ${safeUrl(url)} config empty/direct")
                            return
                        }
                        val originProxy = param.args.getOrNull(0) as? Proxy
                        if (originProxy != proxy) {
                            param.args[0] = proxy
                            Logger.i("[Proxy#openConnection] explicit proxy replaced ${safeUrl(url)} $originProxy -> $proxy")
                        } else {
                            Logger.i("[Proxy#openConnection] ${safeUrl(url)} via $proxy")
                        }
                    }
                }
            )
        }
    }

    /**
     * [Network.openConnection] 会使用指定 Network 的 SocketFactory 直接创建连接，
     * 不保证经过 [URL.openConnection] 的公共入口，因此需要单独 hook。
     *
     * 转调带 [Proxy] 的重载可以同时保留 Network 绑定和强制代理语义。
     */
    private fun hookNetworkOpenConnection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        runCatching {
            XposedHelpers.findAndHookMethod(
                Network::class.java, "openConnection", URL::class.java, object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val network = param.thisObject as? Network ?: return
                        val url = param.args.getOrNull(0) as? URL ?: return
                        if (url.protocol?.startsWith("http") != true) {
                            return
                        }
                        val proxy = resolveProxy()
                        if (proxy == null) {
                            Logger.i("[Proxy#networkOpenConnection] ${safeUrl(url)} config empty/direct")
                            return
                        }
                        param.result = network.openConnection(url, proxy)
                        Logger.i("[Proxy#networkOpenConnection] forced ${safeUrl(url)} via $proxy")
                    }
                }
            )
        }.onFailure {
            Logger.e("[Proxy#networkOpenConnection] hook Network.openConnection(URL) failed: ${it.message}")
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                Network::class.java,
                "openConnection",
                URL::class.java,
                Proxy::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val url = param.args.getOrNull(0) as? URL ?: return
                        if (url.protocol?.startsWith("http") != true) {
                            return
                        }
                        val proxy = resolveProxy()
                        if (proxy == null) {
                            Logger.i("[Proxy#networkOpenConnection] ${safeUrl(url)} config empty/direct")
                            return
                        }
                        val originProxy = param.args.getOrNull(1) as? Proxy
                        if (originProxy != proxy) {
                            param.args[1] = proxy
                            Logger.i(
                                "[Proxy#networkOpenConnection] explicit proxy replaced " +
                                    "${safeUrl(url)} $originProxy -> $proxy"
                            )
                        } else {
                            Logger.i("[Proxy#networkOpenConnection] ${safeUrl(url)} via $proxy")
                        }
                    }
                }
            )
        }.onFailure {
            Logger.e(
                "[Proxy#networkOpenConnection] hook Network.openConnection(URL, Proxy) failed: ${it.message}"
            )
        }
    }

    /**
     * 实际连接执行日志：在底层 HttpURLConnection 真正建连时打点，
     * 可看到该请求最终生效的代理（区别于「是否复用连接」）。
     */
    private fun hookHttpConnect(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val implClasses = listOf(
            "com.android.okhttp.internal.huc.HttpURLConnectionImpl",
            "com.android.okhttp.internal.huc.DelegatingHttpsURLConnection"
        )
        var hooked = false
        for (name in implClasses) {
            runCatching {
                val clazz = XposedHelpers.findClass(name, loadPackageParam.classLoader)
                XposedHelpers.findAndHookMethod(clazz, "connect", object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val conn = param.thisObject as? HttpURLConnection ?: return
                        // 仅日志，任何读取异常都不能影响真实连接。
                        runCatching {
                            Logger.i(
                                "[Proxy#connect] ${conn.requestMethod} ${safeUrl(conn.url)} usingProxy=${conn.usingProxy()}"
                            )
                        }
                    }
                })
            }.onSuccess { hooked = true }
        }
        if (!hooked) {
            Logger.e("[Proxy#connect] no HttpURLConnection impl hooked")
        }
    }

    private fun resolveProxy(): Proxy? {
        val config = HookConfig.proxy()
        if (config.isEmpty()) {
            return null
        }
        val (host, port) = parseProxyConfig(config) ?: run {
            Logger.e("[Proxy#resolve] invalid proxy config")
            return null
        }
        // createUnresolved 避免在被 hook 的线程上做 DNS 解析，交由代理侧解析。
        return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port))
    }

    private fun parseProxyConfig(config: String): Pair<String, Int>? {
        if (config.startsWith("[")) {
            val end = config.indexOf(']')
            if (end <= 1 || end + 2 > config.length || config[end + 1] != ':') {
                return null
            }
            val host = config.substring(1, end)
            val port = config.substring(end + 2).toIntOrNull() ?: return null
            return host.takeIf { it.isNotEmpty() }?.let { it to port }?.takeIf { port in 1..65535 }
        }
        val splitIndex = config.lastIndexOf(':')
        if (splitIndex <= 0 || splitIndex == config.lastIndex) {
            return null
        }
        val host = config.substring(0, splitIndex)
        val port = config.substring(splitIndex + 1).toIntOrNull() ?: return null
        return host.takeIf { it.isNotEmpty() }?.let { it to port }?.takeIf { port in 1..65535 }
    }

    private fun safeUrl(url: URL): String {
        return "${url.protocol}://${url.host}"
    }

    private fun safeUri(uri: URI): String {
        return "${uri.scheme}://${uri.host}"
    }
}
