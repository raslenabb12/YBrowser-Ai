package com.youme.ybrowser

import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {
    private val aiLock = Any()

    @Volatile
    private var currentTabUrl: String = ""

    private var blockedAds  : Int = 0
    private lateinit var urlSearchBar : SearchBar
    private  lateinit var adBlockerAI : AdBlockerAI_v2
    private lateinit var shieldButton : Button
    private lateinit var SearchBarLayout : SearchView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView = findViewById<WebView>(R.id.webView)
        shieldButton  = findViewById<Button>(R.id.button)
        setupwebView()
        shieldButton.setOnClickListener {
            showPopUpMenu(it)
        }
        adBlockerAI = AdBlockerAI_v2(this)

        urlSearchBar = findViewById<SearchBar>(R.id.searchbar)
        SearchBarLayout = findViewById<SearchView>(R.id.searchView)
        SearchBarLayout.editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        SearchBarLayout.editText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {

                var query = v.text.toString()

                if (!query.contains("https") && !query.contains("http")){
                    query = "https://$query"
                }
                webView.loadUrl(query)
                currentTabUrl = query


                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)

                SearchBarLayout.hide()
                true
            } else {
                false
            }
        }
    }
    private fun checkDomain(domain: String, pageUrl1: String, type1: String): Boolean {
        return synchronized(aiLock) {
            try {
                adBlockerAI.shouldBlock(domain,pageUrl1,type1)
            } catch (e: Exception) {
                Log.e("AdBlockAI", "Inference failed for $domain", e)
                false
            }
        }
    }
    private fun setupwebView(){
        val webView = findViewById<WebView>(R.id.webView)
        val settings = webView.settings
        val progressbar = findViewById<ProgressBar>(R.id.progressBar)
        settings.apply {
            javaScriptEnabled = true            // Essential for modern sites
            domStorageEnabled = true           // Required for sites like YouTube/Facebook
            useWideViewPort = true             // Better mobile rendering
            loadWithOverviewMode = true
            builtInZoomControls = true         // Enable pinch-to-zoom
            displayZoomControls = false        // Hide the ugly zoom buttons
            setSupportMultipleWindows(true)    // Support for opening new tabs
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressbar.progress = newProgress
                if (newProgress == 100){
                    progressbar.progress=0
                }
            }
            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)

                val drawable = icon?.scale(50,50)?.toDrawable(resources)

                urlSearchBar.navigationIcon = drawable
            }

        }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    val pageUrl = currentTabUrl
                    val type = when {
                        url.contains(".js") -> "script"
                        url.contains(".png") || url.contains(".jpg") -> "image"
                        else -> "other"
                    }
                    val check =checkDomain(url,pageUrl,type)

                    Log.d("testdata", "shouldInterceptRequest:  $url")
                    if (check) {
                        Log.e("AdBlockAI", " $check --> $url")
                        blockedAds++
                        lifecycleScope.launch {
                            withContext(Dispatchers.Main){
                                shieldButton.text = blockedAds.toString()
                            }
                        }
                        return WebResourceResponse("text/plain", "utf-8",
                            ByteArrayInputStream("".toByteArray())
                        )
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    val pageUrl = currentTabUrl
                    val type = when {
                        url.contains(".js") -> "script"
                        url.contains(".png") || url.contains(".jpg") -> "image"
                        else -> "other"
                    }
                    return checkDomain(url,pageUrl,type)
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    urlSearchBar.setText(url)
                    currentTabUrl = url ?: ""
                    blockedAds =0

                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    urlSearchBar.setText(url)
                }
            }
        }
    private fun showPopUpMenu(anchorView : View){

        val view = layoutInflater.inflate(R.layout.ad_blocker_info_popup, null)
        val countText = view.findViewById<TextView>(R.id.textView)
        countText.text  = "Blocked Ads : $blockedAds"
        val popupWindow = PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.showAsDropDown(anchorView)
    }

}
