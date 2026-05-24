# ArchDesk

ArchDesk is a single-user architecture client management app. This MVP includes client records, many projects per client, meeting notes, project payment ledgers, search, sort, filters, and JWT login.

## Local Development

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the backend:

```bash
mvn spring-boot:run
```

Run the frontend:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

Default development login:

- Email: `admin@archdesk.local`
- Password: `admin123`

Override these with `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD`.

## Production Notes

- Netlify hosts only the React frontend. Deploy the Spring Boot backend separately and set `VITE_API_BASE_URL` in Netlify to that backend URL.
- The backend CORS configuration currently allows `https://archdesk.netlify.app`.
- Change `APP_JWT_SECRET` to a long random value.
- Change the default admin password before deployment.
- The backend uses PostgreSQL and creates/updates tables through Hibernate `ddl-auto=update` for the MVP.
- Draft version tracking, uploads, invoicing, client portal, AI, and native mobile apps are intentionally out of scope for this version.
