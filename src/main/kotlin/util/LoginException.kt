package util

class LoginException(message: String): Exception() {
    val msg = message
    override fun toString(): String {
        return msg
    }
}