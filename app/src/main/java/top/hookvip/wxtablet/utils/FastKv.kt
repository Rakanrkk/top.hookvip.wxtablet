package top.hookvip.wxtablet.utils

import io.fastkv.FastKV
import io.fastkv.interfaces.FastCipher

class FastKv @JvmOverloads constructor(
    id: String = "WeChatTablet",
    password: String = globalPassword
) {
    private val kv by lazy {
        FastKV.Builder(storePath, id).cipher(Cipher(password, password)).build()
    }

    init {
        if (storePath.isEmpty()) {
            throw RuntimeException("storePath is empty(FastKv.initialize(String path)初始化")
        }
    }

    companion object {
        private var storePath = ""

        private var globalPassword = "|aesKey&&aesIv|"

        /**
         * 初始化 传入文件夹路径
         */
        @JvmStatic
        fun initialize(path: String) {
            storePath = path
        }

        @JvmStatic
        fun setGlobalPassword(password: String) {
            globalPassword = password
        }
    }

    fun getString(key: String): String? = kv.getString(key)

    fun putString(key: String, value: String) {
        kv.putString(key, value)
    }

    fun remove(key: String) {
        kv.remove(key)
    }


    /**
     * fast kv的加密实现接口
     */
    private class Cipher(key: String, iv: String) : FastCipher {
        private var helper = AesHelper(key, iv)

        override fun encrypt(src: ByteArray): ByteArray {
            return helper.encrypt(src)
        }

        override fun decrypt(dst: ByteArray): ByteArray {
            return helper.decrypt(dst)
        }

        override fun encrypt(src: Int): Int {
            return src
        }

        override fun encrypt(src: Long): Long {
            return src
        }

        override fun decrypt(dst: Int): Int {
            return dst
        }

        override fun decrypt(dst: Long): Long {
            return dst
        }
    }
}