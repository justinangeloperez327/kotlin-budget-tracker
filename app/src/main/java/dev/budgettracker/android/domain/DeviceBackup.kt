package dev.budgettracker.android.domain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONObject

/**
 * Local-only backup. The digest detects accidental corruption, NOT malicious edits.
 * Documents are plaintext financial data and must be secured by the user.
 */
data class PreparedDeviceBackup(val data: JSONObject,val counts: Map<String,Int>,
                                val createdAt:String,val budgetMonths:Int)

object DeviceBackup {
    const val MAX_BYTES = 3 * 1024 * 1024
    private const val FORMAT = "dev.budgettracker.android.device-backup"
    private const val VERSION = 1
    fun digest(content:String):String=MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(StandardCharsets.UTF_8))
        .joinToString(""){b->"%02x".format(b.toInt() and 0xff)}

    /** A version 1 import must not silently discard future or omitted modules. */
    private fun complete(snapshot:JSONObject) {
        val allowed=(WorkspaceAudit.arrays + listOf("budgets","categoryBudgets")).toSet()
        val actual=snapshot.keys().asSequence().toSet()
        require(actual==allowed) {
            "Backup modules are incomplete or from a different app version"
        }
    }
    fun encode(snapshot: JSONObject, createdAt: LocalDate=LocalDate.now()):String {
        complete(snapshot)
        val result=WorkspaceAudit.inspect(snapshot)
        require(result.passed) { "Cannot back up records with unresolved integrity issues" }
        val bytes=snapshot.toString()
        val envelope=JSONObject()
            .put("format",FORMAT).put("version",VERSION)
            .put("createdAt",createdAt.toString())
            .put("sha256",digest(bytes))
            .put("data",bytes)
        val serialized=envelope.toString()
        require(serialized.toByteArray(StandardCharsets.UTF_8).size<=MAX_BYTES){
            "Backup file exceeds size limit"
        }
        return serialized
    }

    fun decode(text:String):PreparedDeviceBackup {
        require(text.toByteArray(StandardCharsets.UTF_8).size<=MAX_BYTES){
            "Backup file is larger than the supported limit"
        }
        val envelope=JSONObject(text)
        require(envelope.optString("format")==FORMAT && envelope.optInt("version")==VERSION){
            "This is not a supported Budget Tracker device backup"
        }
        require(envelope.optString("createdAt").let {
            runCatching{LocalDate.parse(it)}.isSuccess
        }){"Backup creation date is invalid"}
        val payload=envelope.getString("data")
        val checksum=envelope.getString("sha256")
        require(checksum.length==64 &&
            MessageDigest.isEqual(
                checksum.toByteArray(StandardCharsets.UTF_8),
                digest(payload).toByteArray(StandardCharsets.UTF_8)
            )){"Backup checksum mismatch: the file may be damaged"}
        val data=JSONObject(payload)
        complete(data)
        // No auto-merge and no accepting a partial workspace as a full backup.
        require(data.has("budgets") && data.has("categoryBudgets") && data.has("expenses")){
            "Backup is missing required finance modules"
        }
        val audit=WorkspaceAudit.inspect(data)
        require(audit.passed){
            "Backup contains inconsistent finance records: "+audit.issues.first().detail
        }
        return PreparedDeviceBackup(data,audit.counts,envelope.getString("createdAt"),
            data.getJSONObject("budgets").length())
    }

    fun readBounded(read: (ByteArray)->Int):String {
        val bytes=java.io.ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        while(true) {
            val count=read(buffer)
            if(count<0)break
            if(count==0)continue
            require(bytes.size()+count<=MAX_BYTES){"Backup file exceeds size limit"}
            bytes.write(buffer,0,count)
        }
        return bytes.toString(StandardCharsets.UTF_8.name())
    }
}
