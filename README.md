# Gowit Kotlin SDK

Kotlin SDK for integrating Gowit's advertising platform into Android applications. This SDK provides comprehensive functionality for ad serving and event reporting.

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Ad Requests](#ad-requests)
- [Event Reporting](#event-reporting)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [API Reference](#api-reference)

## Installation

### Gradle

Add the dependency to your app's `build.gradle` file:

```kotlin
dependencies {
    implementation("com.github.gowittechnology:gowit.kt:1.0.4")
}
```

Add JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

## Quick Start

### Basic Setup

```kotlin
import com.gowit.sdk.Gowit

// Configure the SDK with your credentials
Gowit.shared.configure(
    hostname = "https://platform.gowit.com",
    marketplaceId = "MARKETPLACE_UUID"
)
```

```kotlin
import com.gowit.sdk.Gowit

class MyApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize SDK
        Gowit.initialize(this)
        
        // Configure SDK
        Gowit.shared.configure(
            hostname = "https://platform-stage.gowit.com", 
            marketplaceId = "MARKETPLACE_UUID"
        )
    }
}
```

### Configuration with Auto-Impression

When auto-impression is enabled, the platform automatically counts ad responses as impressions, eliminating the need for manual impression reporting.
Before setting it to true please consult to Onboarding Team first:

```kotlin
Gowit.shared.configure(
    hostname = "https://platform.gowit.com",
    marketplaceId = "MARKETPLACE_UUID",
    autoImpressionEnabled = true
)
```

### Request Ads

```kotlin
// Simple ad request
val response = Gowit.shared.getAds(
    placementId = 5,
    sessionId = "user-session-123"
)

response.getAds(5)?.let { ads ->
    println("Retrieved ${ads.size} ads")
}
```

### Report Events

```kotlin
// Report impression
Gowit.shared.sendImpressionEvent(
    adId = "ad-12345",
    sessionId = "user-session-123"
)

// Report click
Gowit.shared.sendClickEvent(
    adId = "ad-12345",
    sessionId = "user-session-123"
)
```

## Ad Requests

### Single Placement Request

```kotlin
val response = Gowit.shared.getAds(
    placementId = 5,
    pageNumber = 0,
    sessionId = "user-session-123"
)
```

### Multiple Placements Request

```kotlin
val placements = listOf(
    PlacementRequest(placementId = 1, maxAds = 5),
    PlacementRequest(placementId = 2, maxAds = 3),
    PlacementRequest(placementId = 3, maxAds = 10)
)

val response = Gowit.shared.getAds(
    placements = placements,
    sessionId = "user-session-123"
)
```

### Builder API (Recommended)

The Builder API provides a fluent interface for constructing ad requests with enhanced readability and flexibility:

#### Basic Builder Usage

```kotlin
val request = AdRequestBuilder(placementId = 11)
    .with(sessionId = "user-session-123")
    .with(pageNumber = 0)

val response = Gowit.shared.getAds(request)
```

#### Builder with Product Context

```kotlin
val products = listOf(
    ProductRequest(productId = "PROD-001", category = "Electronics > Smartphones"),
    ProductRequest(productId = "PROD-002", category = "Electronics > Accessories")
)

val request = AdRequestBuilder(placementId = 11)
    .with(sessionId = "user-session-123")
    .with(products = products)
    .with(search = "iPhone cases")

val response = Gowit.shared.getAds(request)
```

#### Builder with Customer Context

```kotlin
val customer = Customer(
    id = "customer-123",
    customerId = "external-id-456",
    gender = "Female",
    age = 28,
    city = "San Francisco",
    deviceType = "Mobile"
)

val request = AdRequestBuilder(placementId = 5)
    .with(sessionId = "user-session-123")
    .with(customer = customer)
    .with(search = "summer sandals")
    .with(locationId = "SF-01")

val response = Gowit.shared.getAds(request)
```

### Advanced Placement Filtering

Placement filters use nested arrays where each inner array represents an OR-group, and the outer array applies AND semantics:

```kotlin
val placements = listOf(
    PlacementRequest(
        placementId = 10,
        filters = listOf(
            listOf("brand:apple", "brand:samsung"),      // (brand is Apple OR Samsung)
            listOf("category:electronics"),              // AND (category is Electronics)
            listOf("price:100-500")                     // AND (price is 100-500)
        ),
        maxAds = 5
    )
)
```

## Event Reporting

### Supported Event Types

The SDK supports four event types:
- **impression**: When an ad is displayed to the user
- **click**: When a user interacts with an ad
- **sale**: Whenever an order is made

### Basic Event Reporting

```kotlin
val sessionId = "user-session-123"
val adId = "ad-12345"

// Report impression
Gowit.shared.sendImpressionEvent(adId = adId, sessionId = sessionId)

// Report viewable impression
Gowit.shared.sendViewableImpressionEvent(adId = adId, sessionId = sessionId)

// Report click
Gowit.shared.sendClickEvent(adId = adId, sessionId = sessionId)

// Report sale
val sale = Sale(
    advertiserId = "advertiser-789",
    quantity = 2,
    unitPrice = 49.99,
    productId = "product-456"
)
Gowit.shared.sendSaleEvent(sales = listOf(sale), sessionId = sessionId)
```

### Convenience Methods with Ad Objects

```kotlin
val response = Gowit.shared.getAds(placementId = 5, sessionId = "session-123")

response.getAds(5)?.forEach { ad ->
    // Report impression using convenience method
    Gowit.shared.sendImpressionEvent(ad, sessionId = "session-123")
    
    // Report click using convenience method
    Gowit.shared.sendClickEvent(ad, sessionId = "session-123")
}
```

### Bulk Sale Reporting

```kotlin
val sales = listOf(
    Sale(advertiserId = "adv-001", quantity = 1, unitPrice = 99.99, productId = "prod-001"),
    Sale(advertiserId = "adv-002", quantity = 2, unitPrice = 29.99, productId = "prod-002")
)

Gowit.shared.sendSaleEvent(sales = sales, sessionId = "session-456")
```

## Supported Ad Types

### Sponsored Display Ads

The SDK supports display ads that contain:
- `img_url`, or `html`, and `redirect` information
- Ready to display directly to users

### Ad Type Detection

```kotlin
val response = Gowit.shared.getAds(placementId = 5, sessionId = "session-123")

response.allAds.forEach { ad ->
    if (ad.isDisplayAd) {
        // Handle display ad - show image directly
        println("Display Image: ${ad.displayImageUrl ?: "none"}")
    }
}
```

## Rendering Display Ads

The Ad platform returns display ads ready for rendering. Following code block is just a reference, the best setup depends on your specific use-case(s) & scenario(s)

```kotlin
@Composable
fun AdListingView(
    placementId: Int,
    sessionId: String
) {
    var ads by remember { mutableStateOf<List<Ad>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(placementId) {
        try {
            val response = Gowit.shared.getAds(
                placementId = placementId,
                sessionId = sessionId
            )
            ads = response.getAds(placementId) ?: emptyList()
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }
    
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Text(
                text = "Error: $error",
                color = MaterialTheme.colors.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        else -> {
            LazyColumn {
                items(ads) { ad ->
                    AdCard(
                        ad = ad,
                        sessionId = sessionId
                    )
                }
            }
        }
    }
}

@Composable
fun AdCard(
    ad: Ad,
    sessionId: String
) {
    var hasReportedImpression by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable {
                // Report click
                GlobalScope.launch {
                    try {
                        Gowit.shared.sendClickEvent(ad, sessionId)
                    } catch (e: Exception) {
                        Log.e("AdCard", "Failed to send click event", e)
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Display ad image if available
            ad.displayImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Advertisement",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Display HTML content if available
            ad.html?.let { htmlContent ->
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            loadData(htmlContent, "text/html", "UTF-8")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
            
            Text(
                text = "Sponsored",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
    
    // Report impression when ad becomes visible
    LaunchedEffect(ad.adId) {
        if (!hasReportedImpression) {
            try {
                Gowit.shared.sendImpressionEvent(ad, sessionId)
                hasReportedImpression = true
            } catch (e: Exception) {
                Log.e("AdCard", "Failed to send impression event", e)
            }
        }
    }
}
```

## Error Handling

### Comprehensive Error Handling

```kotlin
try {
    val response = Gowit.shared.getAds(
        placementId = 5,
        sessionId = "test-session"
    )
    
    if (response.allAds.isEmpty()) {
        println("No ads available")
    } else {
        println("Retrieved ${response.allAds.size} ads successfully")
    }
    
} catch (error: GowitError) {
    when (error) {
        is GowitError.InvalidPlacements -> {
            println("Error: Invalid placement configuration")
        }
        is GowitError.SerializationError -> {
            println("Error: Failed to serialize request")
        }
        is GowitError.ConfigurationError -> {
            println("Configuration error: ${error.message}")
        }
        is GowitError.InvalidAdId -> {
            println("Error: Invalid ad ID provided")
        }
        is GowitError.HttpError -> {
            println("HTTP error: ${error.httpError}")
        }
        is GowitError.DeserializationError -> {
            println("Deserialization error: ${error.error}")
        }
        is GowitError.NetworkError -> {
            println("Network error: ${error.message}")
        }
    }
} catch (error: Exception) {
    println("Unexpected error: $error")
}
```

## API Reference

### Core Classes

#### Gowit

The main SDK interface providing ad request and event reporting functionality.

**Configuration Methods:**
- `configure(hostname:marketplaceId:)`
- `configure(hostname:marketplaceId:autoImpressionEnabled:)`

**Ad Request Methods:**
- `getAds(placementId:pageNumber:sessionId:) -> AdResponse`
- `getAds(placements:pageNumber:sessionId:) -> AdResponse`
- `getAds(AdRequestBuilder) -> AdResponse`

**Event Reporting Methods:**
- `sendImpressionEvent(adId:sessionId:)`
- `sendViewableImpressionEvent(adId:sessionId:)`
- `sendClickEvent(adId:sessionId:)`
- `sendSaleEvent(sales:sessionId:)`

#### AdRequestBuilder

Fluent interface for constructing ad requests with enhanced readability.

**Initialization:**
- `AdRequestBuilder(placementId:Int)`
- `AdRequestBuilder(placementIds:List<Int>)`
- `AdRequestBuilder(placements:List<PlacementRequest>)`

**Configuration Methods:**
- `with(sessionId:String) -> AdRequestBuilder`
- `with(pageNumber:Int) -> AdRequestBuilder`
- `with(customer:Customer) -> AdRequestBuilder`
- `with(products:List<ProductRequest>) -> AdRequestBuilder`
- `with(search:String) -> AdRequestBuilder`
- `with(category:String) -> AdRequestBuilder`

### Data Models

#### AdResponse
Contains the response from ad requests with convenience methods for accessing ads by placement.

**Standard Methods:**
- `getAds(placementId:Int) -> List<Ad>?` - Get ads for specific placement
- `allAds: List<Ad>` - Get all ads from all placements
- `hasAds(placementId:Int) -> Boolean` - Check if placement has ads
- `adCount(placementId:Int) -> Int` - Get ad count for placement

**Display Ad Methods:**
- `allDisplayAds: List<Ad>` - Get all display ads
- `getDisplayAds(placementId:Int) -> List<Ad>?` - Get display ads for placement
- `hasDisplayAds(placementId:Int) -> Boolean` - Check if placement has display ads
- `displayAdCount(placementId:Int) -> Int` - Get display ad count for placement

#### Ad
Represents an individual ad with type detection capabilities.

**Ad Type Detection:**
- `isDisplayAd: Boolean` - True if ad contains img_url, html, or redirect
- `displayImageUrl: String?` - Returns img_url if this is a display ad
- `clickUrl: String?` - Returns redirect URL if available

#### PlacementRequest
Represents a request for a specific ad placement with optional filtering and limiting.

#### ProductRequest
Simplified model for product context in ad requests.

#### Sale
Model for reporting sales transactions with advertiser, quantity, price, and product information.

#### Customer
Model for providing customer context to improve ad targeting.

## Requirements

- Android 7.0 (API level 24)+
- Kotlin 1.9.20+
- Java 17

## Support

For technical support and documentation:
- GitHub Issues: [Report Issues](https://github.com/gowittechnology/gowit.kt/issues)
- Documentation: [Official Documentation](https://docs.gowit.com)
- Contact: support@gowit.com

## License

This SDK is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
