package io.github.barryxc.wukong.shared

data class BrandProfile(
    val label: String,
    val brand: String,
    val manufacturer: String,
)

data class DeviceProfile(
    val label: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String = device,
    val board: String = device,
    val hardware: String,
)

object DeviceProfiles {
    private const val FALLBACK_BRAND = "google"

    val brands = listOf(
        BrandProfile("Redmi - Redmi", "Redmi", "Xiaomi"),
        BrandProfile("小米 - Xiaomi", "Xiaomi", "Xiaomi"),
        BrandProfile("vivo - vivo", "vivo", "vivo"),
        BrandProfile("OPPO - OPPO", "OPPO", "OPPO"),
        BrandProfile("三星 - samsung", "samsung", "samsung"),
        BrandProfile("Google - google", "google", "Google"),
        BrandProfile("传音 TECNO - TECNO", "TECNO", "TECNO"),
        BrandProfile("传音 Infinix - Infinix", "Infinix", "INFINIX"),
        BrandProfile("传音 itel - itel", "itel", "itel"),
        BrandProfile("华为 - HUAWEI", "HUAWEI", "HUAWEI"),
        BrandProfile("荣耀 - HONOR", "HONOR", "HONOR"),
    )

    private val devicesByBrand = mapOf(
        "Redmi" to listOf(
            DeviceProfile("Redmi Note 13 Pro", "Redmi", "Redmi Note 13 Pro", "garnet", hardware = "qcom"),
            DeviceProfile("Redmi K70", "Redmi", "Redmi K70", "vermeer", hardware = "qcom"),
            DeviceProfile("Redmi 12C", "Redmi", "Redmi 12C", "earth", hardware = "mt6768"),
        ),
        "Xiaomi" to listOf(
            DeviceProfile("Xiaomi 14", "Xiaomi", "Xiaomi 14", "houji", hardware = "qcom"),
            DeviceProfile("Xiaomi 13 Pro", "Xiaomi", "Xiaomi 13 Pro", "nuwa", hardware = "qcom"),
            DeviceProfile("Xiaomi 12S Ultra", "Xiaomi", "Xiaomi 12S Ultra", "thor", hardware = "qcom"),
        ),
        "vivo" to listOf(
            DeviceProfile("vivo X100", "vivo", "vivo X100", "PD2309", hardware = "mt6989"),
            DeviceProfile("vivo X90 Pro", "vivo", "vivo X90 Pro", "PD2242", hardware = "mt6985"),
            DeviceProfile("vivo Y78+", "vivo", "vivo Y78+", "PD2271", hardware = "qcom"),
        ),
        "OPPO" to listOf(
            DeviceProfile("OPPO Find X7", "OPPO", "OPPO Find X7", "PHZ110", hardware = "mt6989"),
            DeviceProfile("OPPO Find X6 Pro", "OPPO", "OPPO Find X6 Pro", "PGEM10", hardware = "qcom"),
            DeviceProfile("OPPO Reno11", "OPPO", "OPPO Reno11", "PJH110", hardware = "mt6896"),
        ),
        "samsung" to listOf(
            DeviceProfile("SM-S9210", "samsung", "SM-S9210", "e1q", hardware = "qcom"),
            DeviceProfile("SM-S9180", "samsung", "SM-S9180", "dm3q", hardware = "qcom"),
            DeviceProfile("SM-A5460", "samsung", "SM-A5460", "a54x", hardware = "s5e8835"),
        ),
        "google" to listOf(
            DeviceProfile("Pixel 8", "google", "Pixel 8", "shiba", product = "shiba", board = "shiba", hardware = "zuma"),
            DeviceProfile("Pixel 8 Pro", "google", "Pixel 8 Pro", "husky", product = "husky", board = "husky", hardware = "zuma"),
            DeviceProfile("Pixel 7", "google", "Pixel 7", "panther", product = "panther", board = "panther", hardware = "gs201"),
        ),
        "TECNO" to listOf(
            DeviceProfile("TECNO CK8n", "TECNO", "TECNO CK8n", "TECNO-CK8n", product = "CK8n", board = "CK8n", hardware = "mt6893"),
            DeviceProfile("TECNO LI9", "TECNO", "TECNO LI9", "TECNO-LI9", product = "LI9", board = "LI9", hardware = "mt6833"),
            DeviceProfile("TECNO KJ7", "TECNO", "TECNO KJ7", "TECNO-KJ7", product = "KJ7", board = "KJ7", hardware = "mt6789"),
        ),
        "Infinix" to listOf(
            DeviceProfile("Infinix X6871", "Infinix", "Infinix X6871", "Infinix-X6871", product = "X6871", board = "X6871", hardware = "mt6893"),
            DeviceProfile("Infinix X6833B", "Infinix", "Infinix X6833B", "Infinix-X6833B", product = "X6833B", board = "X6833B", hardware = "mt6789"),
            DeviceProfile("Infinix X6711", "Infinix", "Infinix X6711", "Infinix-X6711", product = "X6711", board = "X6711", hardware = "mt6789"),
        ),
        "itel" to listOf(
            DeviceProfile("itel P661N", "itel", "itel P661N", "itel-P661N", product = "P661N", board = "P661N", hardware = "ums9230"),
            DeviceProfile("itel S666LN", "itel", "itel S666LN", "itel-S666LN", product = "S666LN", board = "S666LN", hardware = "ums9230"),
            DeviceProfile("itel A665L", "itel", "itel A665L", "itel-A665L", product = "A665L", board = "A665L", hardware = "mt6761"),
        ),
        "HUAWEI" to listOf(
            DeviceProfile("HUAWEI Mate 60", "HUAWEI", "HUAWEI Mate 60", "ALN-AL00", hardware = "kirin9000s"),
            DeviceProfile("HUAWEI P60 Pro", "HUAWEI", "HUAWEI P60 Pro", "MNA-AL00", hardware = "qcom"),
            DeviceProfile("HUAWEI nova 12", "HUAWEI", "HUAWEI nova 12", "ADA-AL00", hardware = "kirin8000"),
        ),
        "HONOR" to listOf(
            DeviceProfile("HONOR Magic6", "HONOR", "HONOR Magic6", "BVL-AN00", hardware = "qcom"),
            DeviceProfile("HONOR 100 Pro", "HONOR", "HONOR 100 Pro", "MAA-AN10", hardware = "qcom"),
            DeviceProfile("HONOR X50", "HONOR", "HONOR X50", "ALI-AN00", hardware = "qcom"),
        ),
    )

    fun normalizedBrand(brand: String?): String {
        return brands.firstOrNull {
            it.brand == brand || it.brand.equals(brand, ignoreCase = true)
        }?.brand ?: FALLBACK_BRAND
    }

    fun manufacturerForBrand(brand: String?): String {
        return brands.firstOrNull { it.brand == normalizedBrand(brand) }?.manufacturer
            ?: normalizedBrand(brand)
    }

    fun devicesForBrand(brand: String?): List<DeviceProfile> {
        return devicesByBrand[normalizedBrand(brand)]
            ?: devicesByBrand.getValue(FALLBACK_BRAND)
    }

    fun defaultModelForBrand(brand: String?): String {
        if (brand.isNullOrBlank()) {
            return DEFAULT_MODEL
        }
        return devicesForBrand(brand).firstOrNull()?.model ?: DEFAULT_MODEL
    }

    fun isModelForBrand(brand: String?, model: String?): Boolean {
        if (brand.isNullOrBlank()) {
            return model.isNullOrBlank()
        }
        return devicesForBrand(brand).any {
            it.model == model || it.model.equals(model, ignoreCase = true)
        }
    }

    fun profileFor(brand: String?, model: String?): DeviceProfile {
        val normalizedBrand = normalizedBrand(brand)
        return devicesForBrand(normalizedBrand).firstOrNull {
            it.model == model || it.model.equals(model, ignoreCase = true)
        } ?: devicesForBrand(normalizedBrand).first()
    }
}
