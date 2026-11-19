package de.fembiond.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 0.7: „schlagartig“ entfällt; frühere Kategorien Perimenopause/Schlaf/Blutung gehen in „Körper“ auf. */
@androidx.room.DeleteColumn(tableName = "entry_terms", columnName = "sudden")
class Migration4To5 : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        LegacyCategories.STATEMENTS.forEach(db::execSQL)
    }
}

/**
 * Führt Begriffe der früheren Kategorien in „Körper“ zusammen. Gibt es den Begriff dort schon,
 * werden Einträge und Synonyme auf ihn umgehängt und der alte Begriff entfernt.
 */
object LegacyCategories {
    private const val OLD = "('PERIMENOPAUSE','SLEEP_ENERGY','BLEEDING')"
    private const val TWIN = "(SELECT b.id FROM terms b JOIN terms o ON o.label = b.label WHERE o.id = %s AND b.category = 'BODY')"
    private const val HAS_TWIN = "termId IN (SELECT o.id FROM terms o WHERE o.category IN $OLD AND EXISTS (SELECT 1 FROM terms b WHERE b.label = o.label AND b.category = 'BODY'))"
    val STATEMENTS = listOf(
        "UPDATE entry_terms SET termId = ${TWIN.format("entry_terms.termId")} WHERE $HAS_TWIN",
        "UPDATE synonyms SET termId = ${TWIN.format("synonyms.termId")} WHERE $HAS_TWIN",
        "DELETE FROM terms WHERE category IN $OLD AND EXISTS (SELECT 1 FROM terms b WHERE b.label = terms.label AND b.category = 'BODY')",
        "UPDATE terms SET category = 'BODY' WHERE category IN $OLD",
    )
}

@Database(
    entities = [
        PeriodEntity::class, TermEntity::class, SynonymEntity::class, EntryEntity::class, EntryTermEntity::class,
        PanicEntity::class, PanicTermEntity::class, MedPhaseEntity::class, IntakeEntity::class, GadEntity::class,
        EventEntity::class, MrsEntity::class, ProfileEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5, spec = Migration4To5::class)],
)
abstract class FembiondDatabase : RoomDatabase() {
    abstract fun dao(): FembiondDao

    companion object {
        fun create(context: Context): FembiondDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKey.passphrase(context)
            return Room.databaseBuilder(context, FembiondDatabase::class.java, "fembiond.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}

/**
 * Zufälliger Datenbankschlüssel, verschlüsselt mit einem Schlüssel aus dem Android Keystore.
 * Der Klartext-Schlüssel verlässt nie das Gerät.
 */
object DatabaseKey {
    private const val ALIAS = "fembiond_db_key"
    private const val PREFS = "fembiond_key"

    fun passphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString("enc", null)
        val iv = prefs.getString("iv", null)
        if (stored != null && iv != null) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            return cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP))
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        prefs.edit()
            .putString("enc", Base64.encodeToString(cipher.doFinal(raw), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
        return raw
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }
}
