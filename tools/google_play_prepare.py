#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import sys
import urllib.parse
from decimal import Decimal
from pathlib import Path

PACKAGE_NAME = "com.huntingcalls"
GOOGLE_LOCALE = "en-US"
API_HELPER = Path("/Users/serg/Documents/api/appstore/upload_identifier_store_texts.py")

APP_LISTING = {
    "title": "Hunting Calls: All-in-One",
    "shortDescription": "800+ Real Hunting Sounds",
    "fullDescription": """Hunting Calls is your all-in-one hunting companion, transforming your phone into a powerful electronic game caller with over 800 authentic wildlife sounds. Trusted by hunters worldwide, it’s your essential tool for every hunting scenario.

• Massive Library: 800+ professional, studio-quality calls covering 80+ animal species across 6 easy-to-navigate categories:
 - Big Game: Deer, Elk, Moose, Bear, Wild Hog
 - Predators: Coyote, Wolf, Cougar, Mountain Lion
 - Waterfowl: Ducks, Geese, Cranes
 - Upland Birds: Turkey, Quail, Grouse, Dove, Pheasant
 - Small Game: Rabbit, Squirrel, Raccoon
 - Furbearers: Fox, Bobcat, Lynx, Beaver, Badger

• Realistic Audio: High-fidelity recordings tested and proven by professional hunters.

• Works 100% Offline: Reliable performance in remote hunting locations—no signal required.

• Easy-to-use Interface: One-tap playback, quick search, loop & delay options, and glove-friendly, dark mode design.

• Multi-Sound Layering: Combine multiple calls simultaneously for natural realism.

• Field Tips: Species-specific guidance on animal behaviors, optimal call usage, seasonal strategies, and decoy setups.

Join thousands of successful hunters who rely on Hunting Calls to improve their outcomes, from seasoned experts to passionate beginners.

Download Hunting Calls now and gain the ultimate advantage on your next hunt!

Subscribe to unlock all sounds. Manage anytime in Settings.

Terms of Use - https://sifterapps.com/terms.html
Privacy Policy - https://sifterapps.com/policy.html""",
}

PRODUCT_TITLE = "Unlock All Calls"
PRODUCT_DESCRIPTION = "Access 800+ animal calls across all species"
BENEFITS = [
    "Access 800+ animal calls",
    "Unlock all species",
    "No ads",
    "Offline use",
]

SUBSCRIPTIONS = {
    "com.huntingcalls.monthly": ("monthly", "P1M", Decimal("7.99")),
    "com.huntingcalls.yearly": ("yearly", "P1Y", Decimal("39.99")),
}

ONE_TIME_PRODUCTS = {
    "com.huntingcalls.lifetime": ("lifetime", Decimal("99.99")),
}


def load_helper():
    spec = importlib.util.spec_from_file_location("storeapi", API_HELPER)
    module = importlib.util.module_from_spec(spec)
    sys.modules["storeapi"] = module
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


def money(value: Decimal) -> dict[str, object]:
    whole = int(value)
    nanos = int((value - Decimal(whole)) * Decimal(1_000_000_000))
    return {"currencyCode": "USD", "units": str(whole), "nanos": nanos}


def converted_prices(api, token: str, value: Decimal) -> tuple[list[dict[str, object]], dict[str, object], str]:
    code, data = api.gp_request(
        "POST",
        f"/applications/{PACKAGE_NAME}/pricing:convertRegionPrices",
        token,
        {"price": money(value)},
        allow_error=True,
    )
    if not 200 <= code < 300:
        raise RuntimeError(f"convertRegionPrices failed {code}: {data}")
    regional = [
        {
            "regionCode": item["regionCode"],
            "price": item["price"],
            "newSubscriberAvailability": True,
        }
        for item in data["convertedRegionPrices"].values()
    ]
    other = data["convertedOtherRegionsPrice"] | {"newSubscriberAvailability": True}
    return regional, other, data["regionVersion"]["version"]


def converted_one_time_prices(api, token: str, value: Decimal) -> tuple[list[dict[str, object]], dict[str, object], str]:
    regional, other, regions_version = converted_prices(api, token, value)
    purchase_regional = [
        {
            "regionCode": item["regionCode"],
            "price": item["price"],
            "availability": "AVAILABLE",
        }
        for item in regional
    ]
    purchase_other = {
        "usdPrice": other["usdPrice"],
        "eurPrice": other["eurPrice"],
        "availability": "AVAILABLE",
    }
    return purchase_regional, purchase_other, regions_version


def update_listing(api, token: str) -> dict[str, object]:
    code, edit = api.gp_request("POST", f"/applications/{PACKAGE_NAME}/edits", token, allow_error=True)
    if not 200 <= code < 300:
        return {"status": "failed_create_edit", "code": code, "error": edit}
    edit_id = edit["id"]
    locale = urllib.parse.quote(GOOGLE_LOCALE, safe="")
    code, data = api.gp_request(
        "PUT",
        f"/applications/{PACKAGE_NAME}/edits/{edit_id}/listings/{locale}",
        token,
        APP_LISTING,
        allow_error=True,
    )
    if not 200 <= code < 300:
        api.gp_request("DELETE", f"/applications/{PACKAGE_NAME}/edits/{edit_id}", token, allow_error=True)
        return {"status": "failed_listing", "code": code, "error": data}
    code, commit = api.gp_request("POST", f"/applications/{PACKAGE_NAME}/edits/{edit_id}:commit", token, allow_error=True)
    if not 200 <= code < 300:
        return {"status": "failed_commit", "code": code, "error": commit}
    return {"status": "committed", "editId": edit_id}


def ensure_subscription(api, token: str, product_id: str, base_plan_id: str, period: str, price: Decimal) -> dict[str, object]:
    code, existing = api.gp_request("GET", f"/applications/{PACKAGE_NAME}/subscriptions/{product_id}", token, allow_error=True)
    regional, other, regions_version = converted_prices(api, token, price)
    body = {
        "packageName": PACKAGE_NAME,
        "productId": product_id,
        "listings": [
            {
                "languageCode": GOOGLE_LOCALE,
                "title": PRODUCT_TITLE,
                "description": PRODUCT_DESCRIPTION,
                "benefits": BENEFITS,
            }
        ],
        "basePlans": [
            {
                "basePlanId": base_plan_id,
                "autoRenewingBasePlanType": {"billingPeriodDuration": period},
                "regionalConfigs": regional,
                "otherRegionsConfig": other,
            }
        ],
    }
    if code == 200:
        patch_code, patch = api.gp_request(
            "PATCH",
            f"/applications/{PACKAGE_NAME}/subscriptions/{product_id}?updateMask=listings,basePlans&regionsVersion.version={regions_version}",
            token,
            body,
            allow_error=True,
        )
        return {"status": "updated" if 200 <= patch_code < 300 else "failed_update", "code": patch_code, "body": patch}
    create_code, created = api.gp_request(
        "POST",
        f"/applications/{PACKAGE_NAME}/subscriptions?productId={urllib.parse.quote(product_id, safe='')}&regionsVersion.version={regions_version}",
        token,
        body,
        allow_error=True,
    )
    return {"status": "created" if 200 <= create_code < 300 else "failed_create", "code": create_code, "body": created}


def ensure_one_time(api, token: str, product_id: str, purchase_option_id: str, price: Decimal) -> dict[str, object]:
    code, existing = api.gp_request("GET", f"/applications/{PACKAGE_NAME}/oneTimeProducts/{product_id}", token, allow_error=True)
    regional, other, regions_version = converted_one_time_prices(api, token, price)
    body = {
        "packageName": PACKAGE_NAME,
        "productId": product_id,
        "listings": [
            {
                "languageCode": GOOGLE_LOCALE,
                "title": PRODUCT_TITLE,
                "description": PRODUCT_DESCRIPTION,
            }
        ],
        "purchaseOptions": [
            {
                "purchaseOptionId": purchase_option_id,
                "buyOption": {"legacyCompatible": True, "multiQuantityEnabled": False},
                "regionalPricingAndAvailabilityConfigs": regional,
                "newRegionsConfig": other,
            }
        ],
    }
    if code == 200:
        patch_code, patch = api.gp_request(
            "PATCH",
            f"/applications/{PACKAGE_NAME}/onetimeproducts/{product_id}?updateMask=listings,purchaseOptions&regionsVersion.version={regions_version}",
            token,
            body,
            allow_error=True,
        )
        return {"status": "updated" if 200 <= patch_code < 300 else "failed_update", "code": patch_code, "body": patch}
    create_code, created = api.gp_request(
        "PATCH",
        f"/applications/{PACKAGE_NAME}/onetimeproducts/{product_id}?allowMissing=true&updateMask=listings,purchaseOptions&regionsVersion.version={regions_version}",
        token,
        body,
        allow_error=True,
    )
    return {"status": "created" if 200 <= create_code < 300 else "failed_create", "code": create_code, "body": created}


def main() -> None:
    api = load_helper()
    token = api.google_token()
    result = {"listing": update_listing(api, token), "subscriptions": {}, "oneTimeProducts": {}}
    for product_id, args in SUBSCRIPTIONS.items():
        result["subscriptions"][product_id] = ensure_subscription(api, token, product_id, *args)
    for product_id, args in ONE_TIME_PRODUCTS.items():
        result["oneTimeProducts"][product_id] = ensure_one_time(api, token, product_id, *args)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
