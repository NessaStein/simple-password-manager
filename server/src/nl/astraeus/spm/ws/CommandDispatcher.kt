package nl.astraeus.spm.ws

import nl.astraeus.database.transaction
import nl.astraeus.spm.crypt.PasswordHash
import nl.astraeus.spm.model.Lock
import nl.astraeus.spm.model.LockDao
import nl.astraeus.spm.model.User
import nl.astraeus.spm.model.UserDao
import nl.astraeus.spm.util.Tokenizer
import nl.astraeus.spm.web.SimpleWebSocket
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Created by rnentjes on 7-6-16.
 */

fun login(ws: SimpleWebSocket, tk: Tokenizer) {
    val loginName = tk.next()
    val passwordHash = tk.next()

    val found = UserDao.findByName(loginName)

    if (found != null && PasswordHash.validatePassword(passwordHash, found.password)) {
        val locks = LockDao.findByUser(found.name)
        val lock = LockDao.findByUserAndId(found.name, ws.id)

        if (lock != null) {
            ws.blocked = lock.locked
        } else if (locks.isNotEmpty()) {
            ws.blocked = true
            LockDao.insert(Lock(found.name, ws.id, true))
        } else {
            ws.blocked = false
            LockDao.insert(Lock(found.name, ws.id, false))
        }

        ws.user = found
        ws.send("LOGIN", found.encryptedKey, found.getData(), ws.blocked.toString())
    } else {
        ws.sendAlert("Error", "Unable to authenticate user!")
    }
}

fun logout(ws: SimpleWebSocket, tk: Tokenizer) {
    ws.logout()
}

fun register(ws: SimpleWebSocket, tk: Tokenizer) {
    val loginName = tk.next()
    val passwordHash = tk.next()
    val encryptedKey = tk.next()

    val password = PasswordHash.createHash(passwordHash)

    val found = UserDao.findByName(loginName)

    if (found != null) {
        ws.sendAlert("Error", "Username already taken!")
    } else {
        val user = User(loginName, password, encryptedKey)

        UserDao.insert(user)

        ws.user = user
        ws.send("LOGIN", encryptedKey, user.getData())
    }
}

fun saveData(ws: SimpleWebSocket, tk: Tokenizer) {
    val user = ws.user ?: throw IllegalAccessException("No loggedin user found!")
    val data = tk.next()

    transaction {
        user.setData(data)
        user.updated = Date()
        UserDao.update(user)
    }
}

fun updatePassword(ws: SimpleWebSocket, tk: Tokenizer) {
    val user = ws.user ?: throw IllegalAccessException("No loggedin user found!")
    val name = tk.next()
    val password = tk.next()
    val encryptedEncryptionKey = tk.next()

    if (name.equals(user.name)) {
        val passwordHash = PasswordHash.createHash(password)

        transaction {
            user.encryptedKey = encryptedEncryptionKey
            user.password = passwordHash
            user.updated = Date()

            UserDao.update(user)

            ws.send("PASSWORD_UPDATED")
        }
    } else {
        ws.sendAlert("Error", "Wrong user send to server!?")
    }
}

object CommandDispatcher {
    val logger = LoggerFactory.getLogger(CommandDispatcher::class.java)
    val commandLogger = LoggerFactory.getLogger("WS")

    val commands: MutableMap<String, (ws: SimpleWebSocket, tk: Tokenizer) -> Unit> = HashMap()

    init {
        commands.put("OK", ::ok)
        commands.put("LOGIN", ::login)
        commands.put("REGISTER", ::register)
        commands.put("SAVEDATA", ::saveData)
        commands.put("LOGOUT", ::logout)
        commands.put("UPDATE_PASSWORD", ::updatePassword)
    }

    fun handle(ws: SimpleWebSocket, msg: String) {
        val start = System.nanoTime()
        val tk = Tokenizer(msg)
        val cmd = tk.next()

        if (cmd == "OK") {
            // skip
            val time = (System.nanoTime() - start) / 1000000f
            commandLogger.debug(String.format("[%16s] %15s %12sms", cmd, ws.handshakeRequest.remoteIpAddress, time))
        } else {
            try {
                val command = commands[cmd] ?: throw IllegalStateException("Don't know how to handle command [$cmd]")

                commandLogger.debug("Handling $cmd")
                transaction {
                    command.invoke(ws, tk)
                }
            } catch (e: Exception) {
                logger.warn(e.message, e)

                ws.sendAlert("Error", "${e.message}")
            }

            val time = (System.nanoTime() - start) / 1000000f
            commandLogger.info(String.format("[%16s] %15s %12sms", cmd, ws.handshakeRequest.remoteIpAddress, time))
        }
    }

}
