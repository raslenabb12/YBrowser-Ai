## 🛡️ YBrowser: Neural-Net Powered Web Browser

Kotlin TensorFlow Lite Platform

YBrowser is an experimental Android web browser that ditches traditional static
blocklists (like EasyList) in favor of a local, on-device Artificial
Intelligence.

Instead of searching through a massive text file of millions of blocked URLs,
YBrowser uses a Multi-Modal Neural Network (TFLite) to mathematically predict if
a network request is an ad or a tracker in real-time, executing in under 2
milliseconds per request.

## Abstract: The Problem with Traditional Ad Blockers

Traditional ad blockers rely on static lists (e.g., ||ads.example.com^). This
approach has three major flaws:

1.  Memory Bloat: Loading hundreds of thousands of rules into Android's RAM is
    highly inefficient.
2.  Cat-and-Mouse Game: If an ad network changes their domain to new-ads.com,
    the static list breaks until a human manually updates it.
3.  Zero-Day Trackers: Trackers using Domain Generation Algorithms (DGAs) bypass
    static lists entirely.

The Solution: YBrowser extracts 27 real-time features from every HTTP request
made by the WebView and feeds them into a quantized AI model. The AI recognizes
the behavior and mathematical structure of an ad, allowing it to block trackers
it has never even seen before.

 The AI Architecture & Feature Engineering

The underlying AI model analyzes each network request across three distinct
categories:

1. Lexical & Mathematical Features

Trackers often use randomly generated domains or incredibly long URL paths. The
AI extracts:

  - Shannon Entropy: Calculates the randomness of the URL characters. High
    entropy often indicates a DGA tracker.
  - Path Depth: Counts the depth of the directory structure
    (/wp-content/uploads/ads/).
  - Query Parameters: Analyzes the size and structure of tracking tags appended
    to URLs.

2. Contextual & Network Features

  - First-Party vs. Third-Party: By comparing the requested URL to the
    documentUrl (the page the user is currently looking at), the AI heavily
    penalizes third-party background requests.
  - Resource Type: Evaluates if the WebView is requesting a script, image,
    stylesheet, or xmlhttprequest. Ads are almost entirely skewed towards
    scripts and images.

3. Heuristic & Regex Features

  - Dimensionality Checks: Looks for standard IAB ad unit sizes inside the URL
    (e.g., 300x250, 728x90).
  - Ad-Tech Keywords: Scans for subtle inclusions of words like telemetry,
    beacon, or pixel at the boundaries of the URL path.

## Android Implementation Details

Building an AI into a mobile browser requires extreme optimization to prevent
the UI from freezing.

  - Custom Standard Scaler: Neural networks require normalized inputs (between 0
    and 1). Instead of using heavy Python libraries, YBrowser hardcodes the
    means and scales directly into Kotlin, performing (X - Mean) / Scale in
    microseconds.
  - Thread-Safe Inference: Android WebView triggers shouldInterceptRequest
    concurrently across dozens of background threads. The inference engine is
    wrapped in a synchronized(aiLock) block, preventing the XNNPack memory
    allocator from crashing under concurrent loads.
  - Main Thread UI Updates: Blocked ad counters are safely dispatched to the UI
    using lifecycleScope.launch { withContext(Dispatchers.Main) { ... } }.
  - Zero-Latency Dropping: When the AI flags a request (output > 0.5f), the
    browser intercepts it by returning an empty WebResourceResponse, saving the
    user's bandwidth instantly.

## Installation & Build

1.  Clone the repository:
    git clone https://github.com/YourUsername/YBrowser-AI.git
2.  Open the project in Android Studio.
3.  Ensure the ad_blocker_quant.tflite model is present in the
    app/src/main/assets/ directory.
4.  Build and run on your Android device or emulator.
### Note
As an experimental ML model, you may occasionally encounter False
Positives. Adjusting the probability threshold (output > 0.5f) can tune the
aggressiveness of the blocker.

## License

This project is licensed under the MIT License - see the LICENSE file for
details.
