# � Guida Completa al Progetto: 

Benvenuto! Se sei qui è perché vuoi capire esattamente cosa succede "sotto il cofano". In questa guida non ci limiteremo ai titoli, ma analizzeremo la logica di ogni singola parte del progetto, spiegandoti come i vari pezzi collaborano per monitorare Alfresco.

---

## � 1. Lo Scheduler: L'orologio del sistema (`EventScheduler`)

Tutto inizia qui. Immagina lo scheduler come un guardiano che ogni 10 secondi si sveglia e controlla se ci sono novità.

- **Cosa fa**: Incrementa un contatore di esecuzioni (per farci sapere che è vivo) e segna l'ora esatta in cui ha iniziato.
- **La scelta del nodo**: Legge dalle configurazioni quale cartella di Alfresco deve monitorare. Se non la trova o è scritta male, si ferma subito per non fare danni.
- **L'azione**: Chiama il servizio degli eventi per importare i dati. Se trova qualcosa di nuovo, lo scrive nei log con un tono colloquiale (es. *"Trovati 5 nuovi eventi"*).
- **Le statistiche**: Alla fine di ogni giro, ricalcola i grafici che vediamo su Grafana: conta quanti PDF abbiamo, quanti file Word, e quanti eventi ci sono in totale su MongoDB.

---

## 🗺️ 2. Il Mapper: Il traduttore universale (`AlfrescoMapper`)

Alfresco parla una lingua complicata (nodi, proprietà tecniche). Abbiamo creato questo componente per "tradurre" quelle informazioni in qualcosa di semplice per noi.

- **Dati grezzi -> Mappa**: Prende un nodo di Alfresco e lo trasforma in una mappa di dati leggibili. Estrae il nome dell'utente, il nome del file, e soprattutto le date, trasformandole dal formato tecnico a quello che usiamo in Italia.
- **Creazione Entità**: Prepara l'oggetto `EventLog` pronto per essere salvato nel database. Qui avviene un calcolo fondamentale: confronta la data del file con quella odierna per capire quanti "giorni sono trascorsi".
- **Preparazione Report**: Costruisce il `FileReportDTO`. Oltre ai dati base, aggiunge "l'intelligenza": calcola una data di scadenza (fissata a 90 giorni dalla creazione) per avvisare chi deve gestire i documenti.

---

## 🧠 3. EventLogService: Il cervello dell'applicazione

Questo è il componente più complesso perché prende le decisioni.

### La sincronizzazione (`importEventsWithoutDuplicates`)
Quando interroga Alfresco, riceve una lista di file. Ma non vogliamo salvare tutto ogni volta!
1. **Filtro**: confronta quello che arriva da Alfresco con quello che abbiamo già su MongoDB.
2. **Salvataggio**: salva solo ciò che è "nuovo" (basandosi su utente, data ed evento).
3. **Gestione Cancellazioni**: se un file non c'è più su Alfresco ma lo avevamo segnato prima, il sistema crea automaticamente un "Evento di Cancellazione" su MongoDB per non perdere la traccia storica.

### Il trucco del tempo (`checkForDayChanges`)
Anche se nessuno modifica un file, i "giorni trascorsi" aumentano ogni notte. 
- Lo scheduler controlla se il numero di giorni calcolato oggi è maggiore di quello salvato ieri. 
- Se sì, aggiorna il record su MongoDB come se fosse successo un nuovo evento di "aggiornamento temporale". Questo garantisce che i report siano sempre precisi ogni mattina.

---

## 📡 4. I Servizi di Integrazione e Visualizzazione

### `AlfrescoService`
È il braccio operativo che esegue le ricerche su Alfresco. Non fa ragionamenti, esegue solo gli ordini: *"Cerca tutti i documenti sotto questa cartella"*.

### `FileReportService`
Prende i dati "tradotti" dal Mapper e li impacchetta per i Controller. È quello che permette di generare la lista che l'utente vede a schermo.

---

## 🎮 5. I Controller: I cancelletti d'ingresso

Sono gli endpoint che permettono a un essere umano (o a un altro software) di parlare con la nostra app.

- **`EventLogController`**: Permette di vedere cosa c'è "live" su Alfresco o di forzare un'importazione a mano (magari perché non vuoi aspettare lo scheduler di 10 secondi).
- **`FileReportController`**: Restituisce la lista pulita e ordinata dei file con tutti i calcoli fatti (scadenza, giorni passati, ecc.).

---

## � 6. Le Metriche: Il diario di bordo (`MetricsConfig`)

Qui gestiamo come l'app racconta se stessa all'esterno (a Prometheus).
- **Categorizzazione**: Invece di dire solo "file/pdf", il sistema raggruppa le estensioni in categorie umane (Immagini, Documenti Word, Excel).
- **Serie Storica**: Registra quanti eventi accadono ogni giorno, così possiamo vedere nei grafici se ci sono picchi di attività (es. "Lunedì sono stati caricati 500 file").

---

## 💡 In sintesi: Il viaggio di un dato
1. Lo **Scheduler** si sveglia.
2. L'**AlfrescoService** recupera i dati dal server.
3. Il **Mapper** li traduce in "lingua umana".
4. L'**EventLogService** decide se sono nuovi o se è solo passato un giorno.
5. Se sono importanti, vengono salvati su **MongoDB**.
6. Il **Controller** mostra il risultato all'utente.
7. Le **Metriche** aggiornano i grafici per il monitoraggio.

---
*Questa documentazione è pensata per farti capire la logica profonda. Ogni riga di codice che scriverai dovrà incastrarsi in questo flusso.*
