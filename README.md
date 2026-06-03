# AfriCompta+ MVP

**Micro-ERP commercial offline-first pour le commerce africain**  
Développé par **Wise Design** · WhatsApp : +240 555 445 514

---

## 📱 Fonctionnalités MVP

| Module | Description |
|--------|-------------|
| 🛒 Caisse / POS | Panier multi-produits, modes de paiement (espèces / Mobile Money / crédit) |
| 📦 Gestion des produits | CRUD complet, alerte stock critique, recherche par nom/code-barres |
| 💸 Dépenses | Saisie par catégorie, historique |
| 📊 Rapports financiers | CA, Bénéfice brut, Dépenses, Bénéfice net (aujourd'hui / mois / année) |
| 🧾 Facture PDF | Génération native Android (ticket 80mm), partage WhatsApp/Email |
| 🔒 Time-bomb 14j | Verrouillage auto après essai + anti-retour horloge |

---

## 🚀 Build via GitHub Actions

### 1. Créer le dépôt GitHub

```bash
git init
git add .
git commit -m "feat: AfriCompta+ MVP initial"
git remote add origin https://github.com/TON_USERNAME/africomptaplus.git
git push -u origin main
```

### 2. L'APK se build automatiquement

Allez dans l'onglet **Actions** de votre dépôt GitHub.  
Après ~3-5 minutes, téléchargez l'APK dans la section **Artifacts**.

### 3. Build manuel (déclenchement)

Dans GitHub → Actions → "Build AfriCompta+ APK" → **Run workflow**

---

## 🗂️ Architecture

```
app/
├── db/
│   └── DatabaseHelper.java       # SQLite schema + CRUD
├── models/
│   ├── Product.java
│   ├── Sale.java / SaleItem.java
│   ├── Expense.java
│   └── FinancialReport.java
├── services/
│   ├── SecurityManager.java      # Time-bomb 14 jours
│   ├── ReportService.java        # Moteur financier
│   ├── InvoiceManager.java       # PDF natif
│   ├── ProductService.java
│   ├── SaleService.java
│   └── ExpenseService.java
└── activities/
    ├── SplashActivity.java       # Entry point + check sécurité
    ├── LockActivity.java         # Écran verrouillage → WhatsApp
    ├── MainActivity.java         # Dashboard
    ├── SaleActivity.java         # Caisse
    ├── ProductListActivity.java
    ├── ProductFormActivity.java
    ├── SaleHistoryActivity.java
    ├── ExpenseActivity.java
    └── ReportActivity.java
```

---

## 🔐 Système de licence

- **14 jours d'essai** gratuits, 3 écrans max
- Anti-cheat : détection de retour en arrière de l'horloge système
- À expiration : écran rouge verrouillé + bouton WhatsApp Wise Design
- Activation par code → contacter **+240 555 445 514**

---

## 📦 Stack technique

- **Langage** : Java pur (Android SDK)
- **Base de données** : SQLite natif (SQLiteOpenHelper, zéro ORM)
- **PDF** : `android.graphics.pdf.PdfDocument` (zéro librairie tierce)
- **APK cible** : minSdk 21 (Android 5.0+), targetSdk 34

---

*AfriCompta+ — Wise Design © 2024*
