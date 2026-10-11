package app.moneymanager.core.categorize

import app.moneymanager.core.categorize.Categories.BILLS
import app.moneymanager.core.categorize.Categories.EDUCATION
import app.moneymanager.core.categorize.Categories.ENTERTAINMENT
import app.moneymanager.core.categorize.Categories.FOOD
import app.moneymanager.core.categorize.Categories.FUEL
import app.moneymanager.core.categorize.Categories.GROCERIES
import app.moneymanager.core.categorize.Categories.HEALTH
import app.moneymanager.core.categorize.Categories.INVESTMENTS
import app.moneymanager.core.categorize.Categories.RENT
import app.moneymanager.core.categorize.Categories.SHOPPING
import app.moneymanager.core.categorize.Categories.SUBSCRIPTIONS
import app.moneymanager.core.categorize.Categories.TRANSPORT
import app.moneymanager.core.categorize.Categories.TRAVEL

/**
 * Merchant/VPA keyword -> category. Seeded into the rules table on first launch, editable in Settings.
 * Matching is on word starts, and the longest matching keyword wins ("swiggy instamart" beats "swiggy",
 * "amazon prime" beats "amazon"). Keywords of 4 letters or fewer must match a whole word ("ola" ≠ "motorola").
 */
object SeedRules {
    private fun cat(category: String, vararg keywords: String) = keywords.map { it to category }

    val all: List<Pair<String, String>> = buildList {
        addAll(cat(FOOD,
            "swiggy", "zomato", "eatsure", "dominos", "domino's", "pizza hut", "kfc", "mcdonald", "mcdonalds", "burger king",
            "subway", "starbucks", "cafe coffee day", "ccd", "chaayos", "haldiram", "barbeque nation", "behrouz", "faasos",
            "box8", "freshmenu", "rebel foods", "eatfit", "dunkin", "baskin", "wow momo", "biryani", "restaurant", "cafe",
            "dhaba", "bakery", "bakers", "sweets", "chai", "tea stall", "juice", "kitchen", "food", "foods", "canteen", "mess",
            "pizza", "burger", "zomato gold", "magicpin", "thirdwave", "blue tokai", "taco bell", "wendy", "theobroma",
            "keventers", "naturals ice cream", "ice cream", "hotel", "bhavan", "bhawan", "udupi",
        ))
        addAll(cat(GROCERIES,
            "blinkit", "grofers", "zepto", "bigbasket", "big basket", "bbnow", "jiomart", "dmart", "d mart", "avenue supermarts",
            "more retail", "more supermarket", "reliance fresh", "reliance smart", "smart bazaar", "spencers", "spencer's",
            "nature's basket", "natures basket", "swiggy instamart", "instamart", "dunzo", "milkbasket", "country delight",
            "licious", "freshtohome", "kirana", "supermarket", "provision", "provisions", "general store", "vegetables",
            "fruits", "dairy", "milk", "amazon fresh", "flipkart minutes", "ratnadeep", "star bazaar", "nilgiris", "bb daily",
        ))
        addAll(cat(SHOPPING,
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "tatacliq", "snapdeal", "firstcry",
            "lenskart", "decathlon", "ikea", "croma", "reliance digital", "vijay sales", "shoppers stop", "lifestyle",
            "westside", "pantaloons", "max fashion", "h&m", "zara", "uniqlo", "reliance trends", "bewakoof", "boat",
            "apple india", "samsung", "xiaomi", "puma", "adidas", "nike", "bata", "titan", "tanishq", "caratlane",
            "pepperfry", "urban ladder", "purplle", "mamaearth", "the souled store", "snitch", "fabindia", "biba", "w for woman",
            "miniso", "hamleys", "crossword", "jockey", "levis", "allen solly", "van heusen", "peter england",
        ))
        addAll(cat(TRANSPORT,
            "uber", "ola", "olacabs", "ani technologies", "rapido", "roppen", "blusmart", "namma yatri", "meru", "metro rail", "metro card",
            "dmrc", "bmrc", "mmrc", "bmtc", "ksrtc", "msrtc", "tsrtc", "upsrtc", "fastag", "toll", "parking", "nhai", "yulu",
            "bounce", "vogo", "indrive", "quick ride", "zoomcar", "revv", "chalo", "ncmc",
        ))
        addAll(cat(FUEL,
            "hpcl", "hindustan petroleum", "bpcl", "bharat petroleum", "iocl", "indian oil", "indianoil", "shell", "nayara",
            "essar oil", "reliance petroleum", "jio bp", "jiobp", "petrol", "fuel", "filling station",
            "service station", "petro", "fuels",
        ))
        addAll(cat(BILLS,
            "jio", "reliance jio", "airtel", "vodafone", "vodafone idea", "bsnl", "mtnl", "act fibernet", "act broadband",
            "hathway", "tata play", "tatasky", "tata sky", "dish tv", "d2h", "sun direct", "bescom", "msedcl", "mahadiscom",
            "tata power", "adani electricity", "adani gas", "bses", "tpddl", "tneb", "tangedco", "kseb", "cesc",
            "torrent power", "uppcl", "pspcl", "mgvcl", "dgvcl", "ugvcl", "pgvcl", "apepdcl", "tsspdcl", "electricity",
            "water board", "bwssb", "mahanagar gas", "indraprastha gas", "igl", "mgl", "piped gas", "lpg", "indane",
            "hp gas", "bharat gas", "broadband", "recharge", "postpaid", "prepaid", "bbps", "billdesk", "municipal",
            "property tax", "maintenance", "excitel", "you broadband", "jiofiber", "airtel xstream", "insurance",
            "policybazaar", "acko", "digit insurance", "hdfc ergo", "icici lombard", "star health",
        ))
        addAll(cat(RENT, "rent", "house rent", "nobroker", "nestaway", "stanza living", "zolo", "colive", "pg rent"))
        addAll(cat(SUBSCRIPTIONS,
            "netflix", "spotify", "prime video", "amazon prime", "primevideo", "hotstar", "disney", "jiocinema", "jio cinema",
            "jiohotstar", "sonyliv", "sony liv", "zee5", "youtube premium", "youtube", "google one", "google storage",
            "google play", "apple.com", "apple services", "icloud", "itunes", "audible", "kindle", "linkedin", "chatgpt",
            "openai", "anthropic", "claude.ai", "notion", "microsoft", "office 365", "adobe", "canva", "github", "dropbox",
            "zoom", "gaana", "wynk", "jiosaavn", "saavn", "apple music", "times prime", "swiggy one", "aha", "mubi",
            "lionsgate", "altbalaji", "discovery", "curiositystream", "medium", "substack", "patreon",
        ))
        addAll(cat(ENTERTAINMENT,
            "bookmyshow", "pvr", "inox", "cinepolis", "district app", "ticketnew", "steam", "playstation", "xbox", "dream11",
            "mpl", "my11circle", "imagica", "wonderla", "smaaash", "timezone", "insider", "paytm insider", "skillbox",
            "cinema", "multiplex", "games", "gaming", "concert", "zomato live",
        ))
        addAll(cat(HEALTH,
            "apollo", "apollo pharmacy", "pharmeasy", "netmeds", "1mg", "tata 1mg", "medplus", "practo", "cult", "cultfit",
            "cure.fit", "curefit", "healthifyme", "hospital", "clinic", "pharmacy", "medical", "medicals", "chemist",
            "diagnostics", "lal path", "thyrocare", "dr lal", "metropolis", "dental", "gym", "fitness", "wellness",
            "truemeds", "orange health", "healthians", "max healthcare", "fortis", "manipal", "narayana", "aster",
        ))
        addAll(cat(EDUCATION,
            "byju", "byjus", "unacademy", "udemy", "coursera", "upgrad", "vedantu", "physicswallah", "physics wallah",
            "simplilearn", "great learning", "duolingo", "school", "college", "university", "academy", "tuition",
            "institute", "classes", "coaching", "scaler", "educative", "leetcode", "skillshare", "masterclass", "edx",
        ))
        addAll(cat(TRAVEL,
            "irctc", "makemytrip", "mmt", "goibibo", "cleartrip", "yatra", "ixigo", "easemytrip", "redbus", "abhibus",
            "indigo", "interglobe", "air india", "vistara", "akasa", "spicejet", "airasia", "air asia", "oyo", "airbnb",
            "booking.com", "agoda", "trivago", "taj hotels", "marriott", "hyatt", "itc hotels", "lemon tree", "treebo",
            "fabhotels", "zostel", "thomas cook", "railway", "airlines", "airport", "visa fee", "vfs",
        ))
        addAll(cat(INVESTMENTS,
            "zerodha", "groww", "upstox", "angel one", "angelone", "angel broking", "kite", "coin by zerodha", "paytm money",
            "kuvera", "etmoney", "et money", "smallcase", "indmoney", "dhan", "5paisa", "icici direct", "icicidirect",
            "hdfc securities", "kotak securities", "motilal", "sharekhan", "mutual fund", "sip", "nps", "ppf", "nsdl",
            "cdsl", "clearing corp", "iccl", "indian clearing", "nsccl", "lic", "sbi mf", "axis mf", "mirae", "parag parikh",
            "ppfas", "nippon india", "uti mf", "aditya birla sun life", "hdfc mf", "kfintech", "kfin", "cams", "camsonline",
            "bsestarmf", "bse star", "wint wealth", "jar app", "gullak", "fixed deposit", "recurring deposit",
        ))
    }
}
