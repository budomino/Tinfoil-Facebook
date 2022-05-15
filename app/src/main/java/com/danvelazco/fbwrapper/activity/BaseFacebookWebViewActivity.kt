/*
 * Copyright (C) 2013 Daniel Velazco
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.danvelazco.fbwrapper.activity

import android.app.Activity
import com.danvelazco.fbwrapper.webview.FacebookWebViewClient.WebViewClientListener
import com.danvelazco.fbwrapper.webview.FacebookWebChromeClient.WebChromeClientListener
import android.net.ConnectivityManager
import android.webkit.CookieSyncManager
import com.danvelazco.fbwrapper.webview.FacebookWebView
import android.widget.ProgressBar
import android.webkit.WebSettings
import android.webkit.ValueCallback
import android.os.Bundle
import com.danvelazco.fbwrapper.R
import android.widget.FrameLayout
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.webkit.WebView.HitTestResult
import android.webkit.WebView
import android.text.TextUtils
import com.danvelazco.fbwrapper.util.WebViewProxyUtil
import android.os.Build
import android.annotation.SuppressLint
import android.net.NetworkInfo
import com.danvelazco.fbwrapper.util.OrbotHelper
import android.webkit.WebChromeClient
import android.app.AlertDialog
import android.content.*
import android.content.res.Configuration
import android.webkit.WebChromeClient.FileChooserParams
import com.squareup.picasso.Picasso
import android.graphics.Bitmap
import com.squareup.picasso.Picasso.LoadedFrom
import android.os.AsyncTask
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import android.os.Environment
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import com.danvelazco.fbwrapper.util.Logger
import com.squareup.picasso.Target
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

/**
 * Base activity that uses a [FacebookWebView] to load the Facebook
 * site in different formats. Here we can implement all the boilerplate code
 * that has to do with loading the activity as well as lifecycle events.
 *
 *
 * See [.onActivityCreated]
 * See [.onWebViewInit]
 * See [.onResumeActivity]
 */
abstract class BaseFacebookWebViewActivity : Activity(), WebViewClientListener, WebChromeClientListener {
    // Members
    protected var mConnectivityManager: ConnectivityManager? = null
    protected var mCookieSyncManager: CookieSyncManager? = null
    @JvmField
    protected var mWebView: FacebookWebView? = null
    protected var mProgressBar: ProgressBar? = null
    protected var mWebSettings: WebSettings? = null
    protected var mUploadMessage: ValueCallback<Uri>? = null
    protected var mUploadMessageLollipop: ValueCallback<Array<Uri>>? = null
    private var mCreatingActivity = true
    private var mPendingImageUrlToSave: String? = null

    /**
     * BroadcastReceiver to handle ConnectivityManager.CONNECTIVITY_ACTION intent action.
     */
    private val mConnectivityReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        /**
         * {@inheritDoc}
         */
        override fun onReceive(context: Context, intent: Intent) {
            // Set the cache mode depending on connection type and availability
            updateCacheMode()
        }
    }

    /**
     * Called when the Activity is created. Make sure the content view
     * is set here.
     */
    protected abstract fun onActivityCreated()

    /**
     * Called when we are ready to start restoring or loading
     * data in the [FacebookWebView]
     *
     * @param savedInstanceState [Bundle]
     */
    protected abstract fun onWebViewInit(savedInstanceState: Bundle?)

    /**
     * Called anything the activity is resumed. Could be used to
     * reload any type of preference.
     */
    protected abstract fun onResumeActivity()

    /**
     * {@inheritDoc}
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
	    
        Timber.plant(Timber.DebugTree())

        // Create the activity and set the layout
        onActivityCreated()
        mConnectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        mWebView = findViewById<View>(R.id.webview) as FacebookWebView
        mWebView!!.setCustomContentView(findViewById<View>(R.id.fullscreen_custom_content) as FrameLayout)
        mWebView!!.setWebChromeClientListener(this)
        mWebView!!.setWebViewClientListener(this)
        WebView.setWebContentsDebuggingEnabled(true)
        mWebSettings = mWebView!!.webSettings
        mProgressBar = findViewById<View>(R.id.progress_bar) as ProgressBar

        // Set the database path for this WebView so that
        // HTML5 Storage API works properly
        mWebSettings!!.setAppCacheEnabled(true)
        mWebSettings!!.databaseEnabled = true

        // Create a CookieSyncManager instance and keep a reference of it
        mCookieSyncManager = CookieSyncManager.createInstance(this)
        registerForContextMenu(mWebView)

        // Have the activity open the proper URL
        onWebViewInit(savedInstanceState)
    }

    /**
     * {@inheritDoc}
     */
    public override fun onResume() {
        super.onResume()

        // Pass lifecycle events to the WebView
        mWebView!!.onResume()

        // Start synchronizing the CookieSyncManager
        mCookieSyncManager!!.startSync()

        // Set the cache mode depending on connection type and availability
        updateCacheMode()

        // Register a Connectivity action receiver so that we can update the cache mode accordingly
        registerReceiver(mConnectivityReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        // Horrible lifecycle hack
        if (mCreatingActivity) {
            mCreatingActivity = false
            return
        }

        // Resume this activity properly, reload preferences, etc.
        onResumeActivity()
    }

    /**
     * {@inheritDoc}
     */
    public override fun onPause() {

        // Un-register the connectivity changed receiver
        unregisterReceiver(mConnectivityReceiver)
        if (mWebView != null) {
            // Pass lifecycle events to the WebView
            mWebView!!.onPause()
        }
        if (mCookieSyncManager != null) {
            // Stop synchronizing the CookieSyncManager
            mCookieSyncManager!!.stopSync()
        }
        super.onPause()
    }

    /**
     * {@inheritDoc}
     */
    override fun onSaveInstanceState(outState: Bundle) {
        // Save the current time to the state bundle
        outState.putLong(KEY_SAVE_STATE_TIME, System.currentTimeMillis())

        // Save the state of the WebView as a Bundle to the Instance State
        mWebView!!.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    /**
     * {@inheritDoc}
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        // Handle orientation configuration changes
        super.onConfigurationChanged(newConfig)
    }


    /**
     * {@inheritDoc}
     */
    override fun onCreateContextMenu(menu: ContextMenu, view: View, menuInfo: ContextMenuInfo) {
        val result = mWebView!!.hitTestResult
        when (result.type) {
            HitTestResult.IMAGE_TYPE -> showLongPressedImageMenu(menu, result.extra)
            HitTestResult.SRC_ANCHOR_TYPE -> showLongPressedLinkMenu(menu, result.extra)
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            ID_CONTEXT_MENU_SAVE_IMAGE -> saveImageToDisk(mPendingImageUrlToSave)
        }
        return super.onContextItemSelected(item)
    }

    /**
     * Set a proxy for the [com.danvelazco.fbwrapper.webview.FacebookWebView]
     *
     * @param host [String]
     * @param port [int]
     */
    protected fun setProxy(host: String?, port: Int) {
        if (mWebView != null && !TextUtils.isEmpty(host) && port > 0) {
            WebViewProxyUtil.setProxy(applicationContext, mWebView, host, port)
        }
    }

    /**
     * Restore the state of the [FacebookWebView]
     *
     * @param inState [Bundle]
     */
    protected fun restoreWebView(inState: Bundle?) {
        if (mWebView != null) {
            mWebView!!.restoreState(inState!!)
        }
    }

    /**
     * Set the browser user agent to be used. If the user agent should be forced,
     * make sure the 'force' param is set to true, otherwise the devices' default
     * user agent will be used.
     *
     * @param force  [boolean]
     * true if we should force a custom user agent, false if not.
     * Note, if this flag is false the default user agent will be
     * used while disregarding the mobile [boolean] parameter
     * @param mobile [boolean]
     * true if we should use a custom user agent for mobile devices,
     * false if not.
     */
    protected fun setUserAgent(force: Boolean, mobile: Boolean, facebookBasic: Boolean) {
        if (force && mobile && !facebookBasic) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mWebSettings!!.userAgentString = USER_AGENT_MOBILE_OLD
            } else {
                mWebSettings!!.userAgentString = USER_AGENT_MOBILE
            }
        } else if (force && !mobile && !facebookBasic) {
            mWebSettings!!.userAgentString = USER_AGENT_DESKTOP
        } else if (force && mobile && facebookBasic) {
            mWebSettings!!.userAgentString = USER_AGENT_BASIC
        } else {
            mWebSettings!!.userAgentString = null
        }
    }

    /**
     * Used to load a new URL in the [FacebookWebView]
     *
     * @param url [String]
     */
    @SuppressLint("SetJavaScriptEnabled")
    protected fun loadNewPage(url: String?) {
        if (mWebView != null) {
            mWebView!!.loadUrl(url!!)
	        mWebView!!.webSettings!!.javaScriptEnabled = true
        }
    }

    /**
     * Method used to allow the user to refresh the current page
     */
    protected fun refreshCurrentPage() {
        if (mWebView != null) {
            mWebView!!.reload()
        }
    }

    /**
     * Method used to allow the user to jump to the top of the webview
     */
    protected fun jumpToTop() {
        loadNewPage("javascript:window.scrollTo(0,0);")
    }

    /**
     * Used to change the geolocation flag.
     *
     * @param allow [boolean] true if the use of
     * geolocation is allowed, false if not
     */
    protected fun setAllowCheckins(allow: Boolean) {
        if (mWebView != null) {
            mWebView!!.setAllowGeolocation(allow)
        }
    }

    /**
     * Used to change to change the behaviour of the [FacebookWebView]<br></br>
     * By default, this [FacebookWebView] will only open URLs in which the
     * host is facebook.com, any other links should be sent to the default browser.<br></br>
     * However, if the user wants to open the link inside this same webview, he could,
     * so in that case, make sure this flag is set to true.
     *
     * @param allow [boolean] true if any domain could be opened
     * on this webview, false if only facebook domains
     * are allowed.
     */
    protected fun setAllowAnyDomain(allow: Boolean) {
        if (mWebView != null) {
            mWebView!!.setAllowAnyDomain(allow)
        }
    }

    /**
     * Used to block network requests of images in the [WebView].
     *
     *
     * See [WebSettings.setBlockNetworkImage]
     *
     * @param blockImages [boolean]
     */
    protected fun setBlockImages(blockImages: Boolean) {
        if (mWebSettings != null) {
            mWebSettings!!.blockNetworkImage = blockImages
        }
    }

    /**
     * Allows us to share the page that's currently opened
     * using the ACTION_SEND share intent.
     */
    protected fun shareCurrentPage() {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_SUBJECT, R.string.share_action_subject)
        i.putExtra(Intent.EXTRA_TEXT, mWebView!!.url)
        startActivity(Intent.createChooser(i, getString(R.string.share_action)))
    }

    /**
     * Show a context menu to allow the user to perform actions specifically related to the link they just long pressed
     * on.
     *
     * @param menu
     * [ContextMenu]
     * @param url
     * [String]
     */
    private fun showLongPressedLinkMenu(menu: ContextMenu, url: String?) {
        // TODO: needs to be implemented, add ability to open site with external browser
    }

    /**
     * Show a context menu to allow the user to perform actions specifically related to the image they just long pressed
     * on.
     *
     * @param menu
     * [ContextMenu]
     * @param imageUrl
     * [String]
     */
    private fun showLongPressedImageMenu(menu: ContextMenu, imageUrl: String?) {
        mPendingImageUrlToSave = imageUrl
        menu.add(0, ID_CONTEXT_MENU_SAVE_IMAGE, 0, getString(R.string.lbl_save_image))
    }

    /**
     * This is to be used in case we want to force kill the activity.
     * Might not be necessary, but it's here in case we'd like to use it.
     */
    protected fun destroyWebView() {
        if (mWebView != null) {
            mWebView!!.removeAllViews()
            /** Free memory and destroy WebView  */
            mWebView!!.freeMemory()
            mWebView!!.destroy()
            mWebView = null
        }
    }

    /**
     * Check whether this device has internet connection or not.
     *
     * @return [boolean]
     */
    private fun checkNetworkConnection(): Boolean {
        return try {
            val networkInfo = mConnectivityManager!!.activeNetworkInfo
            networkInfo?.isConnected ?: false
        } catch (e: SecurityException) {
            // Catch the Security Exception in case the user revokes the ACCESS_NETWORK_STATE permission
            e.printStackTrace()
            // Let's assume the device has internet access
            true
        }
    }

    /**
     * Update the cache mode depending on the network connection state of the device.
     */
    private fun updateCacheMode() {
        if (checkNetworkConnection()) {
            Logger.d(LOG_TAG, "Setting cache mode to: LOAD_DEFAULT")
            mWebSettings!!.cacheMode = WebSettings.LOAD_DEFAULT
        } else {
            Logger.d(LOG_TAG, "Setting cache mode to: LOAD_CACHE_ONLY")
            mWebSettings!!.cacheMode = WebSettings.LOAD_CACHE_ONLY
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressLint("NewApi")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent) {
        when (requestCode) {
            OrbotHelper.REQUEST_CODE_START_ORBOT -> mWebView!!.reload()
            RESULT_CODE_FILE_UPLOAD -> {
                if (null == mUploadMessage) {
                    return
                }
                val result = if (intent == null || resultCode != RESULT_OK) null else intent.data
                mUploadMessage!!.onReceiveValue(result)
                mUploadMessage = null
            }
            RESULT_CODE_FILE_UPLOAD_LOLLIPOP -> {
                if (null == mUploadMessageLollipop) {
                    return
                }
                mUploadMessageLollipop!!.onReceiveValue(FileChooserParams.parseResult(resultCode,
                        intent))
                mUploadMessageLollipop = null
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onProgressChanged(view: WebView, progress: Int) {
        Logger.d(LOG_TAG, "onProgressChanged(), progress: $progress")

        // Posts current progress to the ProgressBar
        mProgressBar!!.progress = progress

        // Hide the progress bar as soon as it goes over 85%
        if (progress >= 85) {
            mProgressBar!!.visibility = View.GONE
            if (progress == 100) {
                Timber.d("BaseFacebookWebViewActivity --> url: ${view.url}")
                if (view.url!!.contains(URL_PAGE_HOME)) {
                    Timber.d("BaseFacebookWebViewActivity --> matching URL")
	                view.loadUrl(INIT_URL_MOBILE + URL_PAGE_MESSAGES)
//                    view.settings.javaScriptEnabled = true
//                    view.loadUrl("javascript:(() => { " +
//                                "const arrow = document.getElementById('MBackNavBarLeftArrow'); " +
//                                "arrow.display = 'none';" +
//                                "const thinger = document.getElementById('MBackNavBar'); " +
//                                "thinger.display = 'none'; " +
//                                "console.log('test');})")
                }
            }
        }
        
    }
    
    private var mLocationAlertDialog: AlertDialog? = null

    /**
     * {@inheritDoc}
     */
    override fun showGeolocationDisabledAlert() {
        mLocationAlertDialog = AlertDialog.Builder(this).create()
        if (mLocationAlertDialog != null) {
            mLocationAlertDialog!!.setTitle(getString(R.string.lbl_dialog_alert))
            mLocationAlertDialog!!.setMessage(getString(R.string.txt_checkins_disables))
            mLocationAlertDialog!!.setButton(DialogInterface.BUTTON_NEUTRAL,
                    getString(R.string.lbl_dialog_ok),
                    DialogInterface.OnClickListener { dialog, which ->
                        // Don't do anything here, simply close the dialog
                    })
            mLocationAlertDialog!!.show()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun hideGeolocationAlert() {
        if (mLocationAlertDialog != null && mLocationAlertDialog!!.isShowing) {
            mLocationAlertDialog!!.dismiss()
            mLocationAlertDialog = null
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun openFileChooser(uploadMsg: ValueCallback<Uri>, acceptType: String, capture: String) {
        Logger.d(LOG_TAG, "openFileChooser()")
        mUploadMessage = uploadMsg
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(
                Intent.createChooser(i,
                        getString(R.string.upload_file_choose)),
                RESULT_CODE_FILE_UPLOAD)
    }

    /**
     * {@inheritDoc}
     */
    @SuppressLint("NewApi")
    override fun openFileChooser(filePathCallback: ValueCallback<Array<Uri>>,
                                 fileChooserParams: FileChooserParams): Boolean {
        return try {
            Logger.d(LOG_TAG, "openFileChooser()")
            mUploadMessageLollipop = filePathCallback
            startActivityForResult(
                    Intent.createChooser(fileChooserParams.createIntent(),
                            getString(R.string.upload_file_choose)),
                    RESULT_CODE_FILE_UPLOAD_LOLLIPOP)
            true
        } catch (e: ActivityNotFoundException) {
            mUploadMessageLollipop = null
            false
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onPageLoadStarted(url: String) {
        Logger.d(LOG_TAG, "onPageLoadStarted() -- url: $url")
        mProgressBar!!.visibility = View.VISIBLE
    }

    /**
     * {@inheritDoc}
     */
    override fun onPageLoadFinished(url: String) {
        Logger.d(LOG_TAG, "onPageLoadFinished() -- url: $url")
        mProgressBar!!.visibility = View.GONE
    }

    /**
     * {@inheritDoc}
     */
    override fun openExternalSite(url: String) {
        Logger.d(LOG_TAG, "openExternalSite() -- url: $url")

        // This link is not for a page on my site, launch another Activity
        // that handles this URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)

            // Hack: Facebook uses a linker helper, it's blank when coming back to app
            // from an outside link, so let's attempt to go back to avoid this blank page
            if (mWebView!!.canGoBack()) {
                Logger.d(LOG_TAG, "Attempting to go back to avoid blank page")
                mWebView!!.goBack()
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Override the back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mWebView!!.canGoBack()) {
                // Check to see if there's history to go back to
                mWebView!!.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Save the image on the specified URL to disk
     *
     * @param imageUrl
     * [String]
     */
    private fun saveImageToDisk(imageUrl: String?) {
        if (imageUrl != null) {
            Picasso.with(this).load(imageUrl).into(saveImageTarget)
        }
    }

    private val saveImageTarget: Target = object : Target {
        override fun onBitmapLoaded(bitmap: Bitmap, loadedFrom: LoadedFrom) {
            SaveImageTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, bitmap)
        }

        override fun onBitmapFailed(drawable: Drawable) {
            Toast.makeText(this@BaseFacebookWebViewActivity, getString(R.string.txt_save_image_failed),
                    Toast.LENGTH_LONG).show()
        }

        override fun onPrepareLoad(drawable: Drawable) {
            // Not implemented
        }
    }

    private inner class SaveImageTask : AsyncTask<Bitmap?, Void?, Boolean>() {
        protected override fun doInBackground(vararg p0: Bitmap?): Boolean {
            if (p0 != null) {
                val bitmap = p0[0]!!
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val imageFile = File(directory, System.currentTimeMillis().toString() + ".jpg")
                try {
                    if (imageFile.createNewFile()) {
                        val ostream = FileOutputStream(imageFile)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, ostream)
                        ostream.close()

                        // Ping the media scanner
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imageFile)))
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
            else {
                return false
            }
        }

        override fun onPostExecute(result: Boolean) {
            if (result) {
                Toast.makeText(this@BaseFacebookWebViewActivity, getString(R.string.txt_save_image_success),
                        Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@BaseFacebookWebViewActivity, getString(R.string.txt_save_image_failed),
                        Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        // Constants
        private const val LOG_TAG = "BaseFacebookWebViewActivity"
        protected const val RESULT_CODE_FILE_UPLOAD = 1001
        protected const val RESULT_CODE_FILE_UPLOAD_LOLLIPOP = 2001
        protected const val KEY_SAVE_STATE_TIME = "_instance_save_state_time"
        private const val ID_CONTEXT_MENU_SAVE_IMAGE = 2981279
        const val INIT_URL_MOBILE = "https://m.facebook.com"
        protected const val INIT_URL_DESKTOP = "https://www.facebook.com"
        protected const val INIT_URL_FACEBOOK_ZERO = "https://0.facebook.com"
        protected const val INIT_URL_FACEBOOK_ONION = "https://facebookcorewwwi.onion"
        protected const val URL_PAGE_NOTIFICATIONS = "/notifications.php"
        const val URL_PAGE_MESSAGES = "/messages"
        const val URL_PAGE_HOME = "/home.php"

        // URL for Sharing Links
        // u = url & t = title
        protected const val URL_PAGE_SHARE_LINKS = "/sharer.php?u=%s&t=%s"

        // Desktop user agent (Google Chrome's user agent from a MacBook running 10.9.1
        protected const val USER_AGENT_DESKTOP = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_1) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/31.0.1650.63 Safari/537.36"

        // Mobile user agent (Mobile user agent from a Google Nexus S running Android 2.3.3
        protected const val USER_AGENT_MOBILE_OLD = "Mozilla/5.0 (Linux; U; Android 2.3.3; en-gb; " +
                "Nexus S Build/GRI20) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1"

        // Mobile user agent (Mobile user agent from a Google Nexus 5 running Android 4.4.2
        protected const val USER_AGENT_MOBILE = "Mozilla/5.0 (Linux; Android 5.0; Nexus 5 Build/LRX21O) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/37.0.0.0 Mobile Safari/537.36"

        // Firefox for Android user agent, it brings up a basic version of the site. Halfway between touch site and zero site.
        protected const val USER_AGENT_BASIC = "Mozilla/5.0 (Android; Mobile; rv:13.0) Gecko/13.0 Firefox/13.0"
    }
}