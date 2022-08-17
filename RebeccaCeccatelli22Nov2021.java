/* README
La struttura del programma è rimasta invariata rispetto allo scritto. Durante il compito, dati i tempi ristretti, mi sono concentrata di proposito
prevalentemente sulla logica che costituisce il cuore del programma e sulla sincronizzazione con i semafori. Qui si ritrova lo stesso codice dello scritto
riordinato, arricchito e reso più leggibile. In particolare: per avere un naming più coerente e consistente i nomi di classi, metodi e variabili sono tutti
in italiano e per esteso, per avere stampe pulite ho ridefinito il metodo toString() in tutte le classi, ho incapsulato alcune righe di codice (già 
presenti nello scritto, non concettualmente nuove) in metodi a parte che racchiudono specifiche funzionalità. Il codice è affiancato da alcuni commenti.

Qualche osservazione sui risultati dell’esecuzione. La somma fra persone ricoverate in ospedale, in attesa, e in ambiente deve essere sempre uguale ad N;
al momento della terminazione si indica se la persona si trovava in Ambiente o in Ospedale (nel secondo caso, infatti, si vede che cv>100).
Si noti infine che il vaccino effettivamente "funziona”: i vaccinati hanno cv tendenzialmente più bassa degli altri e non sono mai stati in ospedale.
*/
package rebeccaceccatelli22nov2021;


import static java.lang.Thread.sleep;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 *
 * @author Rebecca
 */
public class RebeccaCeccatelli22Nov2021 {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws InterruptedException {
        int N = 120, M = 40, T = 3000, K = 10, V = 30;
        
        Ambiente ambiente = new Ambiente(N);
        Ospedale ospedale = new Ospedale(M, T);
        ContatoreContagiati cc = new ContatoreContagiati();

        Persona[] persone = new Persona[N];
        for (int i = 0; i < N; i++) {
            Posizione posizione = new Posizione(Math.random() * 20, Math.random() * 20);
            int caricaVirale = i < K ? 50 : 0;   //righe 28-30: logica di generazione dei parametri da passare ai thread Persona
            boolean vaccinata = (i >= K && i < (K + V)) ? true : false;

            persone[i] = new Persona(posizione, caricaVirale, vaccinata, ambiente, ospedale, cc);
            persone[i].setName("" + (i + 1));
            ambiente.aggiungiPersona(persone[i]);
            persone[i].start();
        }

        for (int i = 0; i < 30; i++) {   //stampe richieste secondo per secondo, per 30 secondi
            System.out.println("Secondo " + (i + 1) + ": ");
            String infoIstantanee = "" + cc + ospedale + ambiente;
            System.out.println(infoIstantanee);

            Thread.sleep(1000);
        }
        System.out.println();

        for (Persona persona : persone) {
            persona.interrupt();
            persona.join();
            System.out.println(persona);   //stampa informazioni finali complete su ogni persona
        }
    }

}

class Posizione {

    double x;
    double y;

    public Posizione(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void aggiorna() {
        double dx = Math.random() * 2 - 1;   //generazione casuale di (dx,dy) spostata all'interno
        double dy = Math.random() * 2 - 1;  // del metodo per rendere il codice più leggibile
        x += dx;
        y += dy;
    }

    public boolean calcolaDistanza(Posizione altraPersona) {
        boolean vicina = false;

        double distanza = Math.sqrt((x - altraPersona.x) * (x - altraPersona.x) + (y - altraPersona.y) * (y - altraPersona.y));
        vicina = distanza < 2 ? true : false;

        return vicina;
    }

    @Override
    public String toString() {   //aggiunto per comodità
        return "(" + x + ", " + y + ")";
    }
}

//invece di usare una variabile statica in Persona si crea un contatore condiviso
class ContatoreContagiati {
                                       //quando i contagiati iniziano ad andare in ospedale e a guarire i
    int nContagiatiAttuali = 0;       //valori di nContagiatiAttuali e nContagiDaInizioPandemia cominciano
    int nContagiDaInizioPandemia = 0; //a differire
    private Semaphore mutex = new Semaphore(1);

    public void incrementa() throws InterruptedException {
        mutex.acquire();
        nContagiatiAttuali++;
        nContagiDaInizioPandemia++;
        mutex.release();
    }

    public void decrementa() throws InterruptedException {
        mutex.acquire();
        if (nContagiatiAttuali > 0) {
            nContagiatiAttuali--;
        }
        mutex.release();
    }

    @Override
    public String toString() {   //aggiunto per comodità
        String statoPandemia = "nContagiatiAttuali (con cv>10 in questo momento): " + nContagiatiAttuali;
        statoPandemia += ", nContagiDaInizioPandemia: " + nContagiDaInizioPandemia + ". ";

        return statoPandemia;
    }
}

class Persona extends Thread {

    Posizione posizione;
    private int caricaVirale = 0;
    private boolean vaccinata;

    private Ambiente ambiente;
    private Ospedale ospedale;
    private ContatoreContagiati cc;

    private int nContagiFatti = 0;
    private int nContagiRicevuti = 0;
    private int nVolteInOspedale = 0;    //ai fini della logica del programma il booleano non serve, è
    private boolean inOspedale = false; //utilizzato solo per stampare il luogo in cui una persona si
                                       //trova istante per istante(ambiente/ospedale)
    private Semaphore mutexCaricaVirale = new Semaphore(1);
    private Semaphore mutexContagiFatti = new Semaphore(1);
    private Semaphore mutexContagiRicevuti = new Semaphore(1);

    public Persona(Posizione posizione, int caricaVirale, boolean vaccinata, Ambiente ambiente, Ospedale ospedale, ContatoreContagiati cc) {
        this.posizione = posizione;
        this.caricaVirale = caricaVirale;
        this.vaccinata = vaccinata;
        this.ambiente = ambiente;
        this.ospedale = ospedale;
        this.cc = cc;
    }

    @Override
    public void run() {
        try {
            boolean contagiata = false, giàSopra10 = false;
            while (true) {
                if (caricaSupera10() && !giàSopra10) {
                    contagiata = true;   //scopo della variabile giàSopra10: si considera un nuovo caso di
                    giàSopra10 = true;  //contagio solo la prima volta che la carica virale di una persona
                    cc.incrementa();   //sale sopra 10. Es: se ad una iterazione cv = 11 e alla successiva
                }                       //cv = 20 non sono due contagi diversi, ma è la stessa persona che 
                if (contagiata) {       //"peggiora" (già presente nello scritto)
                    ambiente.diffondiIlContagio(this);

                    if (staMale()) {
                        ambiente.rimuoviPersona(this);

                        inOspedale = true;
                        nVolteInOspedale++;  //mutex non necessario: incrementata solo dal thread stesso
                        ospedale.ricoveraECuraPersona(this);

                        ospedale.faiUscirePersona(this);
                        inOspedale = false;
                        contagiata = false;  //la prossima volta che sarà cv > 10 andrà considerato come
                        giàSopra10 = false;  //un nuovo caso di contagio
                        cc.decrementa();

                        ambiente.aggiungiPersona(this);
                    }
                }

                posizione.aggiorna();
                diminuisciCaricaVirale();
                sleep(100);
            }
        } catch (InterruptedException ex) {
            System.out.println("Persona " + getName() + " termina.");
        }
    }

    public void aumentaCaricaVirale() throws InterruptedException {
        mutexCaricaVirale.acquire();
        caricaVirale += 5;
        mutexCaricaVirale.release();
    }

    private void diminuisciCaricaVirale() throws InterruptedException {
        mutexCaricaVirale.acquire();
        if (caricaVirale > 0) {
            caricaVirale--;
        }
        mutexCaricaVirale.release();
    }

    public void azzeraCaricaVirale() throws InterruptedException {
        mutexCaricaVirale.acquire();
        caricaVirale = 0;
        mutexCaricaVirale.release();
    }

    public void incrementaContagiFatti() throws InterruptedException {
        mutexContagiFatti.acquire();   //aggiunto rispetto allo scritto
        nContagiFatti++;
        mutexContagiFatti.release();
    }

    public void incrementaContagiRicevuti() throws InterruptedException {
        mutexContagiRicevuti.acquire();   //aggiunto rispetto allo scritto
        nContagiRicevuti++;
        mutexContagiRicevuti.release();
    }

    private boolean caricaSupera10() throws InterruptedException {
        boolean superato = false;
                                       //è una lettura di caricaVirale: il mutex non sarebbe stettamente
        mutexCaricaVirale.acquire();  //necessario, ma dato che è una variabile molto acceduta ho scelto
        superato = caricaVirale > 10 ? true : false;   //di metterlo per sicurezza per essere sicura di 
        mutexCaricaVirale.release();                 //trovarla in uno stato valido

        return superato;
    }

    private boolean staMale() throws InterruptedException {
        boolean staMale = false;

        mutexCaricaVirale.acquire();
        staMale = caricaVirale > 100 ? true : false;
        mutexCaricaVirale.release();

        return staMale;
    }

    public boolean èVulnerabile() {     //le righe di codice di questo metodo erano già presenti nello
        boolean vulnerabile = false;  //scritto in aumentaCarica(); ho scelto di incapsularle in un metodo
                                      //a parte solo per rendere più leggibile il codice
        if (!vaccinata) {
            vulnerabile = true;
        } else {
            int extractedValue = (int) (Math.random() * 10);
            if (extractedValue == 0) {
                vulnerabile = true;
            }
        }

        return vulnerabile;
    }

    @Override       //aggiunto per comodità, qui c'è l'utilizzo del booleano inOspedale commentato prima
    public String toString() {
        String infos = "Persona " + getName() + ": ";
        infos += "posizione: " + posizione + ", cv: " + caricaVirale;
        infos += vaccinata ? ", vaccinata. " : ", non vaccinata. ";
        infos += inOspedale ? " In questo momento in ospedale. " : "In questo momento in ambiente. ";
        infos += "nContagiFatti: " + nContagiFatti + ", nContagiRicevuti: " + nContagiRicevuti + ", nVolteInOspedale: " + nVolteInOspedale + ". ";
        return infos;
    }
}

class Ambiente {

    private Persona[] persone;   //struttura dati diversa rispetto allo scritto: array invece di arrayList
    private Semaphore mutex = new Semaphore(1);   //vedi metodo diffondiIlContagio() per il perchè
    private Semaphore piene = new Semaphore(0);

    private final static Persona IN_OSPEDALE = null;   //costante di classe:
                                                      //vedi metodo diffondiIlContagio() per il perchè
    public Ambiente(int N) {
        persone = new Persona[N]; 
    }

    public void aggiungiPersona(Persona persona) throws InterruptedException {
        mutex.acquire();   //non serve semaforo vuote perchè si sa già che non si aggiungerà oltre N
        persone[Integer.valueOf(persona.getName()) - 1] = persona;
        mutex.release();

        piene.release();
    }

    public void rimuoviPersona(Persona persona) throws InterruptedException {
        piene.acquire();
                           //rimozione simulata: si mettono momentaneamente a null le persone in ospedale
        mutex.acquire();   //vedi metodo diffondiIlContagio() per il perchè
        persone[Integer.valueOf(persona.getName()) - 1] = IN_OSPEDALE;
        mutex.release();
    }

    private int getSize() throws InterruptedException {
        mutex.acquire();
        int size = 0;
        for (Persona persona : persone) {
            size += persona != IN_OSPEDALE ? 1 : 0;
        }
        mutex.release();

        return size;
    }
    
    /*
    Ho riflettuto molto su quale struttura dati usare e come questa dovesse essere gestita affinchè non
    si perdesse il multithreading sul metodo diffondiIlContagio(). Per permettermi di spiegare meglio le
    motivazioni di tale scelta, Le chiedo di cliccare su questo link: https://drive.google.com/file/d/1zeDA7zDrVofbZbXFj-Qr9dhIhS77UK4c/view?usp=sharing
    */
    public void diffondiIlContagio(Persona persona) throws InterruptedException {
        for (Persona altraPersona : persone) {

            if (altraPersona != persona && altraPersona != IN_OSPEDALE) {
                boolean vicina = persona.posizione.calcolaDistanza(altraPersona.posizione);

                if (vicina && altraPersona.èVulnerabile()) {
                    altraPersona.aumentaCaricaVirale();
                    altraPersona.incrementaContagiRicevuti();
                    persona.incrementaContagiFatti();
                }
            }
        }
    }

    @Override
    public String toString() {   //aggiunto per comodità
        String info = "";
        try {
            info = "Persone in ambiente: " + getSize() + ". ";
        } catch (InterruptedException ex) {
        }
        return info;
    }
}

class Ospedale {

    private ArrayList<Persona> personeRicoverate = new ArrayList<>();
    private Semaphore mutex = new Semaphore(1);
    private Semaphore piene = new Semaphore(0);
    private Semaphore vuote;

    private int nPersoneInOspedale, nPersoneInAttesa;
    private Semaphore mutexPersoneInAttesa = new Semaphore(1);
    private int T;

    public Ospedale(int M, int T) {
        vuote = new Semaphore(M);
        this.T = T;
    }

    public void ricoveraECuraPersona(Persona persona) throws InterruptedException {
        incrementaPersoneInAttesa();
        vuote.acquire();
        decrementaPersoneInAttesa();

        mutex.acquire();
        personeRicoverate.add(persona);
        mutex.release();

        piene.release();
                                      //nello scritto l'azzeramento di caricaVirale era fatto in run()
        Thread.sleep(T);              //da Persona stessa, qui ho scelto di spostarlo in Ospedale perchè
        persona.azzeraCaricaVirale(); //logicamente è un compito che spetta ad esso
    }

    public void faiUscirePersona(Persona persona) throws InterruptedException {
        piene.acquire();

        mutex.acquire();
        personeRicoverate.remove(persona);
        mutex.release();

        vuote.release();
    }

    private void incrementaPersoneInAttesa() throws InterruptedException {
        mutexPersoneInAttesa.acquire();
        nPersoneInAttesa++;
        mutexPersoneInAttesa.release();
    }

    private void decrementaPersoneInAttesa() throws InterruptedException {
        mutexPersoneInAttesa.acquire();
        nPersoneInAttesa--;
        mutexPersoneInAttesa.release();
    }

    private int getSize() throws InterruptedException {
        mutex.acquire();
        int size = personeRicoverate.size();
        mutex.release();

        return size;
    }

    @Override
    public String toString() {   //aggiunto per comodità
        String info = "";
        try {
            info = "Persone ricoverate in ospedale: " + getSize();
            info += ", persone in attesa di ricovero: " + nPersoneInAttesa + ". ";
        } catch (InterruptedException ex) {
        }
        return info;
    }

}
