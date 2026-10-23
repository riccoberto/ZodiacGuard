package com.zodiacguard.app

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.zodiacguard.app.data.BackupManager
import com.zodiacguard.app.data.GitHubReleaseUpdater
import com.zodiacguard.app.data.NfcBadgeManager
import com.zodiacguard.app.ui.settings.NfcUnlockDialog
import com.zodiacguard.app.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private lateinit var nfcBadgeManager: NfcBadgeManager
    private lateinit var backupManager: BackupManager
    private var pendingApkUri: Uri? = null
    private val installPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val apkUri = pendingApkUri
            pendingApkUri = null
            if (apkUri != null && packageManager.canRequestPackageInstalls()) {
                launchPackageInstaller(apkUri)
            } else if (apkUri != null) {
                Toast.makeText(
                    this,
                    R.string.update_unknown_sources_message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    companion object {
        private const val TAG = "MainActivity"
        private const val UPDATE_PREFERENCES = "app_update_preferences"
        private const val LAST_UPDATE_CHECK_KEY = "last_update_check"
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        nfcBadgeManager = NfcBadgeManager(this)
        backupManager = BackupManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
        
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tagFilter = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFiltersArray = arrayOf(techFilter, tagFilter)
        
        techListsArray = arrayOf(
            arrayOf(android.nfc.tech.NfcA::class.java.name),
            arrayOf(android.nfc.tech.NfcB::class.java.name),
            arrayOf(android.nfc.tech.NfcF::class.java.name),
            arrayOf(android.nfc.tech.NfcV::class.java.name),
            arrayOf(android.nfc.tech.IsoDep::class.java.name),
            arrayOf(android.nfc.tech.MifareClassic::class.java.name),
            arrayOf(android.nfc.tech.MifareUltralight::class.java.name),
            arrayOf(android.nfc.tech.Ndef::class.java.name),
            arrayOf(android.nfc.tech.NdefFormatable::class.java.name)
        )

        checkForAppUpdate()
        lifecycleScope.launch(Dispatchers.IO) {
            if (!backupManager.performAutoBackup()) {
                Log.w(TAG, "Backup di avvio non riuscito")
            }
        }
    }

    private fun checkForAppUpdate() {
        val preferences = getSharedPreferences(UPDATE_PREFERENCES, MODE_PRIVATE)
        val lastCheck = preferences.getLong(LAST_UPDATE_CHECK_KEY, 0L)
        if (System.currentTimeMillis() - lastCheck < UPDATE_CHECK_INTERVAL_MS) return

        lifecycleScope.launch {
            try {
                val installedVersion =
                    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                val update = withContext(Dispatchers.IO) {
                    GitHubReleaseUpdater(this@MainActivity)
                        .findAvailableUpdate(installedVersion)
                }
                preferences.edit()
                    .putLong(LAST_UPDATE_CHECK_KEY, System.currentTimeMillis())
                    .apply()
                if (update != null && !isFinishing) showUpdatePrompt(update)
            } catch (exception: Exception) {
                Log.w(TAG, "Controllo aggiornamenti non riuscito", exception)
            }
        }
    }

    private fun showUpdatePrompt(update: GitHubReleaseUpdater.AvailableUpdate) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, update.versionName))
            .setPositiveButton(R.string.update_install) { _, _ -> downloadUpdate(update) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun downloadUpdate(update: GitHubReleaseUpdater.AvailableUpdate) {
        val progress = ProgressBar(this)
        val padding = (24 * resources.displayMetrics.density).toInt()
        val progressContainer = LinearLayout(this).apply {
            setPadding(padding, padding, padding, padding)
            addView(
                progress,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = android.view.Gravity.CENTER }
            )
        }
        val progressDialog = AlertDialog.Builder(this)
            .setTitle(R.string.update_downloading)
            .setView(progressContainer)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    GitHubReleaseUpdater(this@MainActivity).downloadAndVerify(update)
                }
                val apkUri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    apkFile
                )
                progressDialog.dismiss()
                requestInstallPermission(apkUri)
            } catch (exception: Exception) {
                Log.e(TAG, "Download o verifica dell’aggiornamento non riusciti", exception)
                progressDialog.dismiss()
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.update_download_error, exception.message.orEmpty()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun requestInstallPermission(apkUri: Uri) {
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.update_unknown_sources_title)
                .setMessage(R.string.update_unknown_sources_message)
                .setPositiveButton(R.string.update_open_settings) { _, _ ->
                    pendingApkUri = apkUri
                    installPermissionLauncher.launch(
                        Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton(R.string.update_later, null)
                .show()
            return
        }
        launchPackageInstaller(apkUri)
    }

    private fun launchPackageInstaller(apkUri: Uri) {
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        } catch (exception: Exception) {
            Log.e(TAG, "Impossibile avviare l’installatore Android", exception)
            Toast.makeText(
                this,
                getString(R.string.update_download_error, exception.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch(Dispatchers.IO) {
            val success = backupManager.performAutoBackup()
            if (success) {
                Log.d(TAG, "Backup automatico salvato via MediaStore")
            } else {
                Log.w(TAG, "Backup automatico all'uscita non riuscito")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action || 
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, android.nfc.Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }

            if (tag != null) {
                val uid = nfcBadgeManager.getTagUid(tag)
                if (uid != null) {
                    val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                    val currentFragment = navHostFragment?.childFragmentManager?.primaryNavigationFragment
                    
                    if (currentFragment is SettingsFragment) {
                        currentFragment.onNfcTagDetected(tag)
                    } else {
                        lifecycleScope.launch {
                            try {
                                val savedUid = nfcBadgeManager.savedBadgeUid.first()
                                if (savedUid != null && savedUid == uid) {
                                    val dialog = NfcUnlockDialog(uid) {
                                        Log.d(TAG, "Marcatura completata")
                                    }
                                    dialog.show(supportFragmentManager, "nfc_unlock_dialog")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Errore NFC: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }
    }
}
