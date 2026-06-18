package io.github.barryxc.wukong.hook.core

//防止递归锁
object LockHolder {

    //进程->线程级别防递归
    private val recursiveLock = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean? {
            return false
        }
    }

    fun protect(block: () -> Unit) {
        if (recursiveLock.get() == true) {
            return
        }
        try {
            recursiveLock.set(true)
            block()
        } finally {
            recursiveLock.set(false)
        }
    }
}
