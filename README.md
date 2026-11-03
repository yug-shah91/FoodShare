# 🌱 FoodShare Connect — Community Food Rescue Platform

A full-stack web application designed to reduce urban food waste and address local food insecurity. **FoodShare Connect** empowers restaurants, cafes, bakeries, caterers, supermarkets, and individuals to list surplus food on an **interactive map**, enabling community members and NGOs to claim and collect meals safely using **secure 6-digit OTP verification** upon handover.

Built as a lightweight, robust monolithic application: **React (HTML5, CSS3, JavaScript)** on the frontend and **Spring Boot 3 + Spring Security 6** on the backend. No Docker complexity, no microservices overhead, and no external AI dependencies.

---

## 🚀 Key Features

### 1. 🗺️ Interactive Maps & Geolocation (OpenStreetMap + Leaflet)
- **Live Food Rescue Map for Recipients**:
  - Interactive map view displaying available surplus food across the city with custom category icons (🍲 Cooked Meals, 🥖 Bakery, 🥦 Fresh Produce, 🥛 Dairy, 📦 Packaged).
  - Pin popups show food quantity, preparation time, dietary badge, donor rating, and a 1-click **"Claim Food"** button.
  - **"📍 Near Me" GPS button**: Calculates real-time distance in kilometers using the Haversine formula and tags each listing (e.g. `📍 1.2 km away`).
- **Map Pin Location Picker for Donors**:
  - Embedded map in the donation listing form allows donors to click anywhere or drag a pin to set exact pickup coordinates.
  - **"🧭 Use Current GPS"** button automatically detects browser location coordinates.
- **Turn-by-Turn Navigation**:
  - Once claimed, recipients can click **"Open in Google Maps"** to launch turn-by-turn navigation directly to the pickup spot.

### 2. 👤 Comprehensive Donor Profiles & Direct Coordination
- **Rich Donor Cards**:
  - Displays donor full name, organization/business name (e.g., *Annapurna Kitchen & Cafe*), donor category (*Verified Restaurant, Bakery, Supermarket, Community Kitchen, Home Cook*), star rating (⭐ 4.9), and community badges (*🏅 Verified Food Hero*).
- **Direct Contact Unlocked Upon Claiming**:
  - **📞 Call Donor** (`tel:...`) for rapid coordination.
  - **💬 WhatsApp Message** link pre-filled with the claimed food details.
  - Private pickup address and donor's specific pickup notes are protected until an item is claimed.

### 3. 🔐 Cryptographic 6-Digit OTP Handover Verification
- **Digital Pickup Pass**:
  - When a recipient claims food, the backend generates an authentic **6-digit cryptographic OTP** (*e.g., `482 915`*).
  - Recipient gets an interactive **Digital Pickup Pass**:
    - High-contrast 6-digit OTP display with 1-click copy.
    - Live ticking countdown timer to the 2-hour pickup deadline (pulses red when under 15 minutes).
    - SVG QR code voucher.
    - Instructions: *"Show or speak this 6-digit OTP to the donor upon pickup to confirm handover."*
- **Donor Handover Verification Modal**:
  - The donor dashboard provides a **"🔑 Verify Handover OTP"** button on claimed listings.
  - Entering the recipient's 6-digit OTP sends an atomic verification request (`POST /api/donations/{id}/verify-pickup-otp`).
  - Upon matching, the status transitions to `COMPLETED`, bonus community karma points are awarded, and celebratory confetti triggers on screen!

### 4. 📊 Community Impact Analytics & Gamification
- **Live Impact Telemetry Banner**:
  - Real-time community metrics: **Meals Rescued**, **CO₂ Emissions Prevented (kg)**, **Active Available Listings**, and **Total Community Donors**.
- **Dietary & Category Filters**:
  - Quick-filter pills: *All, Cooked Meals, Bakery, Produce, Dairy, Packaged*.
  - Dietary preferences: *🌱 Pure Veg, 🥩 Non-Veg, 🥑 Vegan, ✨ Jain*.
  - Instant live search bar filtering by dish name, area, or donor name.
- **Freshness & Expiry Urgency Bar**:
  - Dynamic gradient health bar showing remaining consume-before shelf life.
  - Items expiring within 2 hours pulse with an animated **"⏳ Urgent Rescue"** badge.
- **Ratings & Gratitude System**:
  - Recipients submit a 1–5 star rating and quick gratitude tags (*"🍲 Freshly Made"*, *"😋 Delicious"*, *"📦 Clean Packaging"*, *"❤️ Kind Donor"*), elevating the donor's community score.
- **Karma Points**:
  - Community members earn karma (+25 for listing food, +50 for completed handovers, +10 for rescues), visible on their profile and navbar.

---

## 🛠️ Technologies & Stack

| Layer | Technology | Details |
|---|---|---|
| **Frontend** | React 18, Vite | Single Page Application (SPA), Component-based architecture |
| **Maps & UI** | Leaflet, OpenStreetMap | Interactive mapping, custom SVG pins, no paid API keys required |
| **Visuals & UX** | HTML5, CSS3, Canvas-Confetti | CSS Variables, Flexbox/Grid, Glassmorphism, animations |
| **Backend** | Java 17+, Spring Boot 3 | Monolithic RESTful API, Controller-Service-Repository pattern |
| **Security** | Spring Security 6, JJWT | Stateless JWT authentication, BCrypt password hashing |
| **Database** | MySQL / H2 | Relational schema, Hibernate ORM, Spring Data JPA, Flyway migrations |
| **Concurrency** | Optimistic Locking (`@Version`) | Atomic database updates preventing race conditions |

---

## 🏗️ Architecture & Core Mechanics

### 1. State Transitions & Expiry Management
```
[ACTIVE] ──(Claimed by recipient)──> [CLAIMED] ──(OTP Handover Verified)──> [COMPLETED]
   │                                     │
   ├──(Donor cancels)──> [CANCELLED]     ├──(Pickup deadline passes: 2 hrs)──> Back to [ACTIVE]
   │                                     │
   └──(Consume-before passes)───────────┴──> [EXPIRED]
```

- **Claim Validity**: Recipients have a 2-hour collection window (capped by the food's consume-before time).
- **Background Clean-Up Job (`ExpiryJob`)**:
  - Runs every minute.
  - Automatically flags food past its consume-before time as `EXPIRED`.
  - Releases abandoned claims back to `ACTIVE` so others can rescue the food, while archiving release metadata in the `claim_releases` table.

### 2. Concurrency & Race-Condition Prevention
- `DonationRepository.claimIfActive`, `completeWithOtp`, and `cancelIfActive` execute atomic database queries:
  ```sql
  UPDATE donations SET status = 'CLAIMED', claimed_by_id = ?, pickup_otp = ?, version = version + 1
  WHERE id = ? AND status = 'ACTIVE' AND expires_at > NOW();
  ```
- If two recipients click "Claim" simultaneously, exactly one row update succeeds (`returns 1`); the other client receives `0` updated rows and a clean `409 Conflict` error.

### 3. Address Privacy & Security
- Public browsing (`GET /api/donations`) exposes only the approximate area (e.g. *"Vijay Nagar, Indore"*) and map coordinates.
- Private exact addresses and donor contact phone numbers are restricted to the donor, the claiming recipient, and administrators.
- Sliding rate limiters protect registration and login endpoints against brute-force attacks and bot spam.

---

## 📋 Getting Started

### Prerequisites
- **Java Development Kit (JDK 17+)**
- **Maven 3.8+**
- **Node.js 18+** and **npm**
- **MySQL** (optional; defaults to embedded H2 database)

### 1. Backend Setup
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Configure database credentials in [`application.properties`](file:///c:/Users/shahy/Downloads/foodshare/backend/src/main/resources/application.properties) if using MySQL (or run with default H2):
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/food_db?createDatabaseIfNotExist=true
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```
3. Build and launch the Spring Boot backend:
   ```bash
   mvn clean spring-boot:run
   ```
   The API will start at `http://localhost:8080`.

### 2. Frontend Setup
1. Open a new terminal and navigate to the frontend directory:
   ```bash
   cd frontend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the Vite development server:
   ```bash
   npm run dev
   ```
4. Open `http://localhost:5173` in your browser.

---

## 📱 User Workflows

### 👩‍🍳 Donor Workflow
1. Click **"New here? Create free account"** and register with role **"Donor"** (select category: *Restaurant, Bakery, Supermarket, Individual*, etc., and enter your contact phone).
2. On the dashboard, fill out the **"List Food Donation"** form:
   - Provide dish name, category, dietary type (Pure Veg, Non-Veg, Vegan, Jain), servings, and storage.
   - Use the embedded interactive map or click **"🧭 Use Current GPS"** to pinpoint the pickup location.
   - Set preparation and consume-before times.
3. Once published, your listing appears on the city-wide live rescue map.
4. When a recipient claims your food, their contact information appears on your listing.
5. When the recipient arrives, click **"🔑 Verify Handover OTP"**, enter their 6-digit code, and complete the exchange!

### 🙋 Recipient Workflow
1. Register with role **"Recipient"**.
2. Browse active food listings using either the **📋 Cards Grid** or **🗺️ Live Map**.
3. Use the search bar, category chips, or dietary filters to find meals. Click **"📍 Near Me"** to sort by distance.
4. Click **"⚡ Claim Food"** on an item.
5. In **"🎫 My Pickups & Passes"**, open your **Digital Pickup Pass**:
   - Check your unique 6-digit OTP and QR code voucher.
   - Review the donor's exact address, pickup instructions, and contact details (call or WhatsApp directly).
   - Use the navigation map to travel to the donor's location.
6. Present your 6-digit OTP code to the donor at pickup.
7. Leave a 5-star rating and thank-you note to celebrate the donor!

---

## 🔌 API Endpoints Reference

### Authentication (`/api/auth`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register new user (Donor / Recipient) with phone and donor profile | Public (Rate-limited) |
| `POST` | `/api/auth/login` | Authenticate with username and password, returns JWT token | Public (Rate-limited) |

### Food Donations (`/api/donations`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/donations` | Get paged list of active, unexpired food listings | Authenticated |
| `GET` | `/api/donations/map` | Get all active listings with GPS coordinates for interactive map | Authenticated |
| `GET` | `/api/donations/impact` | Get live community impact metrics (meals rescued, CO₂ saved) | Authenticated |
| `POST` | `/api/donations` | Create a new food listing with coordinates and dietary type | Donor only |
| `GET` | `/api/donations/mine` | View donor's own listings | Donor only |
| `GET` | `/api/donations/claimed` | View recipient's claimed pickups and digital passes | Recipient only |
| `GET` | `/api/donations/claim-history` | View recipient's released/missed claims history | Recipient only |
| `POST` | `/api/donations/{id}/claim` | Claim an active food listing (generates 6-digit OTP) | Recipient only |
| `POST` | `/api/donations/{id}/verify-pickup-otp` | Verify 6-digit OTP code to confirm physical handover | Donor / Recipient / Admin |
| `POST` | `/api/donations/{id}/complete` | Complete pickup directly | Recipient / Donor |
| `POST` | `/api/donations/{id}/feedback` | Submit 1–5 star rating and gratitude feedback for donor | Recipient only |
| `POST` | `/api/donations/{id}/cancel` | Cancel an active food listing | Donor only |

### Admin Dashboard (`/api/admin`)
| Method | Endpoint | Description | Access |
|---|---|---|---|
| `GET` | `/api/admin/dashboard` | Aggregated rescue statistics and environmental metrics | Admin only |
| `GET` | `/api/admin/users` | Paged users directory with activity counters | Admin only |
| `GET` | `/api/admin/donations` | Full listing history across all platform donations | Admin only |

---

## 🧪 Testing

The backend includes a comprehensive suite of unit and integration tests covering concurrency, optimistic locking, scheduled expiry jobs, rate limiters, OTP verification, and address privacy:

```bash
cd backend
mvn test
```
*Current test suite status: 51/51 tests passing (`BUILD SUCCESS`).*

---

## 📄 License
This project is open-source and available under the [MIT License](LICENSE).
