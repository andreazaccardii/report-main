# 📊 Alfresco Reporting & Monitoring System

Benvenuto nella documentazione ufficiale del progetto `report-main`.
Questa applicazione funge da ponte tra **Alfresco** e il nostro sistema di monitoraggio basato su **PostgreSQL** e **Grafana**.

Il sistema importa regolarmente i metadati dei documenti da Alfresco, li storicizza su un database relazionale e calcola metriche avanzate (come i giorni di giacenza) per la visualizzazione su dashboard.

---

## 🏗️ Architettura del Sistema

Il sistema si basa su tre componenti principali:
1.  **Spring Boot Application** (`report-main`): Il cuore pulsante. Interroga Alfresco tramite API REST, elabora i dati e li salva su DB.
2.  **PostgreSQL**: Il database relazionale che sostituisce MongoDB. Qui vengono salvati sia gli eventi di audit (`event_log`) che lo storico delle sincronizzazioni (`sync_history`).
3.  **Grafana**: L'interfaccia di visualizzazione che legge direttamente da PostgreSQL per creare grafici e report in tempo reale.

---

## 📂 Struttura del Codice

Guida rapida alle classi principali del progetto:

### 1. Core & Configurazione
*   **`ReportApplication`**: Punto di ingresso (`main`). Configura l'avvio e disabilita la sicurezza di default di Spring.

### 2. Il Motore (Scheduler)
*   **`EventScheduler`**: L'orologio del sistema. Ogni 10 secondi (o come configurato), avvia il processo di sincronizzazione chiamando il service.

### 3. Servizi (Logica di Business)
*   **`EventLogService`**: Il "cervello". Gestisce la logica di sincronizzazione:
    *   *Deduplicazione*: Evita di salvare eventi già presenti.
    *   *Tracking Temporale*: Calcola i "giorni trascorsi" e crea eventi sintetici notturni ("Aggiornamento Statistiche").
    *   *Cancellazioni*: Rileva documenti rimossi da Alfresco.
    *   *Metriche*: Salva le statistiche di esecuzione nella tabella `sync_history` (documenti trovati, nuovi eventi, timestamp).
*   **`AlfrescoService`**: Esegue le chiamate HTTP verso le API di Alfresco per cercare i documenti.
*   **`FileReportService`**: Prepara i dati per i report richiesti via API (es. lista file scaduti).
*   **`AlfrescoMapper`**: Traduce i JSON complessi di Alfresco nelle nostre entità Java (`EventLog`).

### 4. Entità (Database)
*   **`EventLog`**: Tabella principale. Usa un campo `jsonb` (`dettagli`) per conservare metadati flessibili (nome file, mimetype, dimensioni).
*   **`SyncHistory`**: Tabella di servizio per tracciare ogni esecuzione dello scheduler (fondamentale per le dashboard di stato).

### 5. API (Controller)
*   **`EventLogController`**: Endpoint per monitoraggio e trigger manuale dell'importazione.
*   **`FileReportController`**: Endpoint per estrarre report sui file.

---

## 🚀 Guida all'Installazione (Docker)

Prerequisiti: **Docker** e **Java 17+**.

### 1. Avviare PostgreSQL
Esegui questo comando per creare il database:
```bash
docker run --name report-postgres -e POSTGRES_PASSWORD=reindex123 -p 5433:5432 -d postgres
```
*Nota: La porta esterna è **5433** per evitare conflitti con altri Postgres locali.*

### 2. Collegare DBeaver (Opzionale)
Per esplorare i dati manualmente:
1.  Apri DBeaver -> Nuova Connessione -> PostgreSQL.
2.  **Host**: `localhost`
3.  **Port**: `5433`
4.  **Database**: `postgres`
5.  **User/Password**: `postgres` / `reindex123`

### 3. Avviare Grafana
```bash
docker run -d -p 3000:3000 --name=grafana grafana/grafana
```
Accedi a `http://localhost:3000` (admin/admin).

### 4. Avviare l'Applicazione
Crea il file `src/main/resources/application.properties` con le credenziali (vedi sezione Configurazione in fondo), poi lancia:
```bash
./mvnw spring-boot:run
```

---

## 📊 Configurazione Completa Grafana

Dopo aver avviato Grafana, segui questi passaggi per creare la Dashboard di monitoraggio.

### 1. Aggiungere Data Source PostgreSQL
1.  Vai su Grafana -> **Connections** -> **Data Sources**.
2.  Clicca **Add new data source** -> Seleziona **PostgreSQL**.
3.  Configura:
    *   **Host**: `report-postgres:5432` (se Grafana è su Docker nella stessa rete) oppure `host.docker.internal:5433` (se Grafana è su host macchina).
    *   **Database**: `postgres`
    *   **User**: `postgres`
    *   **Password**: `reindex123`
    *   **TLS/SSL Mode**: `disable`
4.  Clicca **Save & Test**.

### 2. Query SQL per i Pannelli

Ecco le query SQL pronte all'uso per i tuoi grafici.

#### A. Totale Eventi (Counter)
```sql
SELECT count(*) FROM event_log;
```

#### B. Andamento Giornaliero per Tipo (Stacked Bar Chart)
Mostra l'attività nel tempo, divisa per tipo di evento (Aggiunto, Modificato, ecc.).
```sql
SELECT
  date_trunc('day', data) as time,
  count(*) FILTER (WHERE evento = 'Aggiunto Documento') as "Aggiunti",
  count(*) FILTER (WHERE evento = 'Modificato Documento') as "Modificati",
  count(*) FILTER (WHERE evento = 'Eliminato Documento') as "Eliminati",
  count(*) FILTER (WHERE evento = 'Aggiornamento Statistiche') as "Statistiche"
FROM event_log
-- Opzionale: WHERE to_char(data, 'YYYY-MM-DD') = '${giorno}'
GROUP BY 1
ORDER BY 1;
```
*Tip: Nelle opzioni del grafico, imposta **Stacking** su "Normal".*

#### C. Distribuzione per Tipo File (MimeType) (Pie Chart)
```sql
SELECT 
  dettagli->>'mimeType' as metric, 
  count(*) as value
FROM event_log 
GROUP BY 1
ORDER BY 2 DESC;
```

**🎨 Fix Colori (Per avere colori fissi basati sul tipo):**
1. Nelle opzioni del pannello, vai su **Standard options** -> **Color scheme**.
2. Seleziona **"Classic palette"**.
3. *Opzionale*: Per forzare un colore specifico (es. PDF rosso), vai nel tab **Overrides** -> **Fields with name** -> Seleziona il mimetype -> Aggiungi proprietà **Color scheme** -> **Single color**.

#### D. Documenti "Vecchi" (> 90 giorni) (Table)
```sql
SELECT
  utente,
  struttura,
  (dettagli ->> 'giorniTrascorsi')::int as giorni
FROM event_log
WHERE (dettagli ->> 'giorniTrascorsi')::int > 90
ORDER BY giorni DESC;
```

### 3. Statistiche di Sistema (Dallo Scheduler)
Queste query usano la tabella `sync_history` per monitorare la salute del sistema.

#### A. Documenti Attivi in Alfresco (Stat Panel)
```sql
SELECT documenti_attivi FROM sync_history ORDER BY data_esecuzione DESC LIMIT 1;
```

#### B. Counter Esecuzioni Scheduler (Stat Panel)
```sql
SELECT count(*) FROM sync_history;
```

#### C. Orario Ultima Sincronizzazione (Stat Panel)
*Select type: **Timestamp***
```sql
SELECT data_esecuzione FROM sync_history ORDER BY data_esecuzione DESC LIMIT 1;
```

---

## 🎛️ Filtri Dashboard (Variabili)

Per aggiungere un menu a tendina per filtrare per data:
1.  Vai in **Dashboard Settings** -> **Variables** -> **Add variable**.
2.  **Name**: `giorno` (tutto minuscolo).
3.  **Data source**: PostgreSQL.
4.  **Query**:
    ```sql
    SELECT DISTINCT to_char(data, 'YYYY-MM-DD') AS "__text", to_char(data, 'YYYY-MM-DD') AS "__value" FROM event_log ORDER BY 1 DESC;
    ```
5.  Usa la variabile nei pannelli aggiungendo alla query:
    ```sql
    WHERE to_char(data, 'YYYY-MM-DD') = '${giorno}'
    ```

---

## 🔧 Configurazione Applicazione

Le proprietà principali in `src/main/resources/application.properties`:

```properties
# Porta Server
server.port=8081

# Connessione DB
spring.datasource.url=jdbc:postgresql://localhost:5433/postgres
spring.datasource.username=postgres
spring.datasource.password=reindex123
spring.jpa.show-sql=false

# Connessione Alfresco
content.service.url=http://localhost:8080
content.service.security.basicAuth.username=admin
content.service.security.basicAuth.password=admin
scheduler.node-id=INSERISCI_QUI_IL_TUO_NODE_ID
```
