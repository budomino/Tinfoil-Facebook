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
package com.danvelazco.fbwrapper.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import kotlin.jvm.JvmOverloads
import android.webkit.WebView
import android.webkit.WebSettings.PluginState
import android.webkit.WebSettings
import android.webkit.WebSettings.RenderPriority
import android.webkit.WebViewClient
import com.danvelazco.fbwrapper.webview.FacebookWebViewClient
import com.danvelazco.fbwrapper.webview.FacebookWebChromeClient
import android.widget.FrameLayout
import com.danvelazco.fbwrapper.activity.BaseFacebookWebViewActivity
import com.danvelazco.fbwrapper.webview.FacebookWebChromeClient.WebChromeClientListener
import com.danvelazco.fbwrapper.webview.FacebookWebViewClient.WebViewClientListener
import timber.log.Timber

/**
 * FacebookWebView.<br></br>
 * Extends [android.webkit.WebView].<br></br>
 */
@SuppressLint("SetJavaScriptEnabled")
class FacebookWebView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyle: Int = 0) : WebView(context!!, attrs, defStyle) {
	// Constants, default values for this WebView's configuration
	val DEFAULT_JS_ENABLED = true
	val DEFAULT_PLUGIN_STATE = PluginState.ON_DEMAND
	val DEFAULT_SUPPORT_ZOOM = true
	val DEFAULT_SAVE_FORM_DATA = false
	val DEFAULT_SAVE_PASSWORD = false
	val DEFAULT_DOM_STORAGE_ENABLED = true
	val DEFAULT_ALLOW_GEOLOCATION = false
	val DEFAULT_ALLOW_FILE_UPLOAD = true
	val DEFAULT_WIDE_VIEWPORT = true
	val DEFAULT_LOAD_WITH_OVERVIEW_MODE = true
	val DEFAULT_CACHE_MODE = WebSettings.LOAD_DEFAULT
	val DEFAULT_RENDER_PRIORITY = RenderPriority.HIGH
	val DEFAULT_SCROLLBAR_STYLE = SCROLLBARS_OUTSIDE_OVERLAY
	
	// Members
	private var mInitialized = false
	private var mContext: Context? = null
	
	/**
	 * Most of the [WebSettings] are being handled by the WebView
	 * but this allows flexibility for the activity to furthermore
	 * change other [WebSettings].
	 *
	 * @return [WebSettings]
	 */
	var webSettings: WebSettings? = null
		private set
	private var mWebViewClient: FacebookWebViewClient? = null
	private var mWebChromeClient: FacebookWebChromeClient? = null
	
	/**
	 * Destroy this WebView
	 */
	override fun destroy() {
		mInitialized = false
		if (mWebChromeClient != null) {
			mWebChromeClient!!.destroy()
		}
		if (mWebViewClient != null) {
			mWebViewClient!!.destroy()
		}
		mContext = null
		webSettings = null
		mWebViewClient = null
		mWebChromeClient = null
		super.destroy()
	}
	
	/**
	 * Initialize this WebView and set default values
	 */
	private fun initializeWebView() {
		// Create a new instance of FutebolWebViewClient and keep a reference to it
		mWebViewClient = object : FacebookWebViewClient() {
			override fun onPageCommitVisible(view: WebView?, url: String?) {
				super.onPageCommitVisible(view, url)
				Timber.d("FacebookWebView --> onPageCommitVisible --> URL: $url")
				
			}
		}
		webViewClient = mWebViewClient!!
		
		// New instance of FutebolWebChromeClient and keep a reference to it
		mWebChromeClient = object : FacebookWebChromeClient(mContext) {
			override fun onProgressChanged(view: WebView?, progress: Int) {
				super.onProgressChanged(view, progress)
				if (progress == 100){
					Timber.d("FacebookWebView --> initializeWebView --> progress: $progress")
				}
			}
		}
		webChromeClient = mWebChromeClient
		
		// Get a reference of this WebView's WebSettings
		webSettings = settings
		
		// We can consider this WebView initialized
		mInitialized = true
		
		// Set default values
		setDefaults()
	}
	
	/**
	 * Default settings and configuration for this WebView.
	 */
	private fun setDefaults() {
		// Default WebView style
		scrollBarStyle = DEFAULT_SCROLLBAR_STYLE
		
		// Default WebSettings
		webSettings!!.javaScriptEnabled = DEFAULT_JS_ENABLED
		webSettings!!.pluginState = DEFAULT_PLUGIN_STATE
		webSettings!!.setSupportZoom(DEFAULT_SUPPORT_ZOOM)
		webSettings!!.displayZoomControls = DEFAULT_SUPPORT_ZOOM
		webSettings!!.builtInZoomControls = DEFAULT_SUPPORT_ZOOM
		webSettings!!.saveFormData = DEFAULT_SAVE_FORM_DATA
		webSettings!!.savePassword = DEFAULT_SAVE_PASSWORD
		webSettings!!.domStorageEnabled = DEFAULT_DOM_STORAGE_ENABLED
		webSettings!!.useWideViewPort = DEFAULT_WIDE_VIEWPORT
		webSettings!!.loadWithOverviewMode = DEFAULT_LOAD_WITH_OVERVIEW_MODE
		webSettings!!.cacheMode = DEFAULT_CACHE_MODE
		webSettings!!.setRenderPriority(DEFAULT_RENDER_PRIORITY)
		
		// Default WebChromeClient settings
		mWebChromeClient!!.setAllowGeolocation(DEFAULT_ALLOW_GEOLOCATION)
		mWebChromeClient!!.setAllowFileUpload(DEFAULT_ALLOW_FILE_UPLOAD)
	}
	
	/**
	 * Set the custom view that can be used to add other views.
	 * For example, this could be used for video playback.
	 *
	 * @param view [android.widget.FrameLayout]
	 */
	fun setCustomContentView(view: FrameLayout?) {
		check(!(!mInitialized || mWebChromeClient == null)) { "The WebView must be initialized first." }
		mWebChromeClient!!.setCustomContentView(view)
	}
	
	/**
	 * Set the listener for this WebChromeClient.
	 *
	 * @param listener [FacebookWebChromeClient.WebChromeClientListener]. It must be
	 * in the Activity context.
	 */
	fun setWebChromeClientListener(listener: WebChromeClientListener?) {
		check(!(!mInitialized || mWebChromeClient == null)) { "The WebView must be initialized first." }
		mWebChromeClient!!.setListener(listener)
	}
	
	/**
	 * Allow web applications to access this device's location.<br></br>
	 * Need
	 *
	 * @param allow [boolean] flag stating whether or not to allow
	 * this web application to see the
	 * device's location.
	 */
	fun setAllowGeolocation(allow: Boolean) {
		check(!(!mInitialized || mWebChromeClient == null)) { "The WebView must be initialized first." }
		mWebChromeClient!!.setAllowGeolocation(allow)
	}
	
	/**
	 * Set the listener for this WebViewClient.
	 *
	 * @param listener [FacebookWebViewClient.WebViewClientListener]. It must be
	 * in the Activity context.
	 */
	fun setWebViewClientListener(listener: WebViewClientListener?) {
		check(!(!mInitialized || mWebViewClient == null)) { "The WebView must be initialized first." }
		mWebViewClient!!.setListener(listener)
	}
	
	/**
	 * Whether this WebViewClient should load any domain without
	 * checking the internal domain list.
	 *
	 * @param allow [boolean]
	 */
	fun setAllowAnyDomain(allow: Boolean) {
		check(!(!mInitialized || mWebViewClient == null)) { "The WebView must be initialized first." }
		mWebViewClient!!.setAllowAnyDomain(allow)
	}
	
	/**
	 * {@inheritDoc}
	 */
	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		// Override the back button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			// If the fullscreen view is showing, hide it
			if (mWebChromeClient != null) {
				if (mWebChromeClient!!.isCustomViewVisible) {
					mWebChromeClient!!.onHideCustomView()
					return true
				}
			}
		}
		return super.onKeyDown(keyCode, event)
	}
	/**
	 * Constructor.
	 *
	 * @param context  [Context]
	 * @param attrs    [AttributeSet]
	 * @param defStyle [int]
	 */
	/**
	 * Constructor.
	 *
	 * @param context [Context]
	 * @param attrs   [AttributeSet]
	 */
	/**
	 * Constructor.
	 *
	 * @param context [Context]
	 */
	init {
		
		// Do not try to initialize anything if it's in edit mode (layout editor)
		if (isInEditMode) {
			throw Exception()
		}
		mContext = context
		initializeWebView()
	}
	
}