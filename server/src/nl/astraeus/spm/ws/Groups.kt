package nl.astraeus.spm.ws

import nl.astraeus.database.transaction
import nl.astraeus.spm.model.GroupDao

/**
 * User: rnentjes
 * Date: 23-11-16
 * Time: 11:32
 */

fun createGroup(ws: SimpleWebSocket, tk: Tokenizer) {

}

fun sendGroups(ws: SimpleWebSocket, email: String) {
    transaction {
        for (group in GroupDao.findByUser(email)) {

        }
    }

}