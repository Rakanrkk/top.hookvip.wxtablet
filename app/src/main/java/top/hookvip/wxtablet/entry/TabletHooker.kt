package top.hookvip.wxtablet.entry

import android.app.Application
import android.content.Context
import android.view.View
import androidx.core.view.isGone
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.param.PackageParam
import top.hookvip.wxtablet.data.HostInfo
import top.hookvip.wxtablet.factory.toAppClassOrNull
import top.hookvip.wxtablet.utils.FastKv
import top.hookvip.wxtablet.utils.WXConfig
import java.lang.reflect.Method

object TabletHooker : YukiBaseHooker() {

    override fun onHook() {
        if (!isWeChat()) return

        runCatching {
            doHostInit(this)
            YLog.info("start hook wechat tablet(${HostInfo.toVerStr()})")

            val bootstrap = "com.tencent.tinker.loader.app.TinkerApplication".toClass().method {
                name("onBaseContextAttached")
                param(Context::class.java, Long::class.java, Long::class.java)
            }.give() ?: error("TinkerApplication.onBaseContextAttached not found")

            hookMethod(bootstrap, "bootstrap") {
                afterHook {
                    HostInfo.appClassLoader = (instance as Application).classLoader
                    installTabletHooks()
                }
            }
        }.onFailure { YLog.error("Cannot initialize WeChatTablet", it) }
    }

    private fun installTabletHooks() {
        val methods = WXConfig.resolveMethods()
        methods.checkPadTablet?.let { method ->
            hookMethod(method, "tablet check") {
                afterHook {
                    result = !isChatCall(Throwable().stackTrace)
                }
            }
        } ?: YLog.error("Tablet check hook unavailable")

        methods.visibleLoginButton?.let { method ->
            hookMethod(method, "login button") {
                beforeHook {
                    (args[0] as? View)?.let { loginButton ->
                        if (loginButton.isGone) loginButton.visibility = View.VISIBLE
                    }
                }
            }
        } ?: YLog.warn("Login button hook unavailable")
    }

    private fun hookMethod(
        target: Method,
        label: String,
        configure: YukiMemberHookCreator.MemberHookCreator.LegacyCreator.() -> Unit,
    ) {
        target.declaringClass.hook(isForceUseAbsolute = true) {
            injectMember {
                members(target)
                configure()
            }.onHooked { YLog.info("Installed $label hook: $it") }
                .onAlreadyHooked { YLog.info("$label hook already installed: $it") }
                .onAllFailure { YLog.error("$label hook failed", it) }
        }
    }

    private fun isWeChat(): Boolean {
        return "com.tencent.mm.app.Application".toClassOrNull(appClassLoader) != null
    }

    private fun doHostInit(packageParam: PackageParam) {
        FastKv.initialize("${appInfo.dataDir}/files/WeChatTablet/")
        HostInfo.apply {
            modulePath = packageParam.moduleAppFilePath
            appPackageName = packageParam.packageName
            appClassLoader = requireNotNull(packageParam.appClassLoader)
            appFilePath = packageParam.appInfo.sourceDir
            val buildConfigClass = "com.tencent.mm.boot.BuildConfig".toAppClassOrNull()
            fun readField(name: String): Any? = runCatching {
                buildConfigClass?.getDeclaredField(name)?.apply { isAccessible = true }?.get(null)
            }.getOrNull()

            val buildTag = readField("BUILD_TAG") as? String
            isPlay = buildTag?.contains("_GP_") == true
            verName = readField("VERSION_NAME") as? String ?: ""
            verCode = (readField("VERSION_CODE") as? Number)?.toInt() ?: 0
            clientVer = readField("CLIENT_VERSION_ARM64") as? String ?: ""
            canCacheMethods = buildTag != null && verName.isNotEmpty() && verCode > 0 && clientVer.isNotEmpty()
            if (!canCacheMethods) YLog.warn("Host build metadata incomplete; method cache disabled")
        }
    }
}
