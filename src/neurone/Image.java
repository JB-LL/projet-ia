import java.io.*;
import java.util.*;
import javax.imageio.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.stream.*;

public class Image
{
    static private int LabelChat = 0;
    static private int LabelChien = 1;
    static private int LabelWild = 2;
    static private int LabelInconnu = 3;
    private int label = -1;
    private int largeur = 0;
    private int hauteur = 0;
    private int[] donnees = null; // image applatie en concaténant les lignes les unes après les autres

    public int label() {return label;}
    public int largeur() {return largeur;}
    public int hauteur() {return hauteur;}
    public int taille() {return donnees.length;} // nombre de pixels: hauteur*largeur ou 3*hauteur*largeur pour une image RGB
    public int[] donnees() {return donnees;}

    public boolean estEnNiveauxDeGris() {return taille() == largeur() * hauteur();}

    public void afficheMetadonnees() {
        String type = estEnNiveauxDeGris() ? "grayscale" : " couleurs";
        System.out.printf("Image (%s): label=%d, largeur=%d, hauteur=%d, taille=%d\n",
            type, label(), largeur(), hauteur(), taille());
    }

    public Image(final String cheminImage, int label, boolean niveauxDeGris) {
        try {
            final BufferedImage img = ImageIO.read(new File(cheminImage));
            this.label = label;
            largeur = img.getWidth(null);
            hauteur = img.getHeight(null);
            final int taille = niveauxDeGris ? hauteur*largeur : 3*hauteur*largeur;
            donnees = new int[taille];
            for (int i = 0; i < hauteur; ++i) {
                for (int j = 0; j < largeur; ++j) {
                    final long rgb = img.getRGB(j, i);
                    final int r = (int)((rgb>>16)&255); // Isoler la composante rouge
                    final int g = (int)((rgb>>8)&255);  // Isoler la composante verte
                    final int b = (int)((rgb)&255);     // Isoler la composante bleue
                    final int index = i * largeur + j;
                    if (niveauxDeGris) {
                        final float gris = 0.2125f * r + 0.7154f * g + 0.0721f * b; // RGB -> niveaux de gris
                        donnees[index] = (int) Math.max(0, Math.min(255, gris));
                    }
                    else {
                        donnees[3*index+0] = r;
                        donnees[3*index+1] = g;
                        donnees[3*index+2] = b;
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.printf("Image non trouvée ou non lisible: %s\n", cheminImage);
        }
    }

    public static List<String> listeFichiers(String repertoire) {
        List<String> cheminsFichiers = null;
        try {
            cheminsFichiers = Files.walk(Paths.get(repertoire)) 
                .filter(Files::isRegularFile)                  
                .map(Path::toAbsolutePath)                      
                .map(Path::toString)                            
                .collect(Collectors.toList());                  
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cheminsFichiers;
    }

    // =========================================================================
    // MÉTHODE MAIN ADAPTÉE POUR L'APPRENTISSAGE SUPERVISÉ ET LE TEST FINAL
    // =========================================================================
    public static void main (String[] args)
    {
        // =========================================================================
        //  MENU INTERACTIF : DEMANDER LE CHOIX À L'UTILISATEUR TOUT AU DÉBUT !
        // =========================================================================
        Scanner clavier = new Scanner(System.in);
        
        System.out.println("==================================================");
        System.out.println("  BIENVENUE DANS L'ENTRAÎNEMENT DE L'IA");
        System.out.println("==================================================");
        System.out.println("Quelle fonction d'activation voulez-vous utiliser ?");
        System.out.println("  1 - HEAVISIDE (Basique, sortie binaire 0 ou 1)");
        System.out.println("  2 - SIGMOIDE  (Très stable, bornée entre 0 et 1)");
        System.out.println("  3 - RELU      (Puissante mais risque d'explosion)");
        System.out.print("\n Votre choix (1, 2 ou 3) : ");
        
        int choixFonction = clavier.nextInt(); // Le programme fait une pause et attend ta réponse !
        
        System.out.println("==================================================\n");

        // ---------------------------------------------------------------------
        // PARTIE 1 : ENTRAÎNEMENT DU NEURONE
        // ---------------------------------------------------------------------
        System.out.println("--- DÉBUT DE LA PHASE D'ENTRAÎNEMENT ---");
        System.out.println("Recherche des images dans ../../dataset_animaux/train/ ...");
        
        List<String> tousLesCheminsTrain = listeFichiers("../../dataset_animaux/train/");
        if (tousLesCheminsTrain == null || tousLesCheminsTrain.isEmpty()) {
            System.out.println("Erreur : Aucune image d'entraînement trouvée.");
            return;
        }

        // Filtre pour ne garder que les chats et les chiens
        List<String> cheminsTrain = new ArrayList<>();
        for (String chemin : tousLesCheminsTrain) {
            if (chemin.contains("cat") || chemin.contains("dog")) {
                cheminsTrain.add(chemin);
            }
        }

        Collections.shuffle(cheminsTrain); 
        int nbImagesTrain = cheminsTrain.size();
        System.out.println(nbImagesTrain + " images de chats/chiens prêtes pour l'entraînement.");

        Image premiereImage = new Image(cheminsTrain.get(0), LabelInconnu, true);
        int nbEntrees = premiereImage.taille(); 
        
        float[][] toutesLesEntrees = new float[nbImagesTrain][nbEntrees];
        float[] toutesLesConsignes = new float[nbImagesTrain];

        System.out.println("Chargement et normalisation des images d'entraînement...");
        for (int i = 0; i < nbImagesTrain; i++) {
            String chemin = cheminsTrain.get(i);
            
            float consigne = chemin.contains("cat") ? (float)LabelChat : (float)LabelChien;
            toutesLesConsignes[i] = consigne;

            Image img = new Image(chemin, chemin.contains("cat") ? LabelChat : LabelChien, true);
            int[] pixelsBruts = img.donnees();

            for (int j = 0; j < nbEntrees; j++) {
                toutesLesEntrees[i][j] = pixelsBruts[j] / 255.0f;
            }
        }

        // =========================================================================
        // LE CHOIX EST UTILISÉ ICI POUR CRÉER LE BON NEURONE
        // =========================================================================
        iNeurone monNeurone = null;
        String fichierSauvegarde = "";
        String nomFonction = "";

        switch (choixFonction) {
            case 1:
                monNeurone = new NeuroneHeavyside(nbEntrees);
                fichierSauvegarde = "cerveau_heaviside.txt";
                nomFonction = "HEAVISIDE";
                Neurone.fixeCoefApprentissage(0.005f); 
                break;
                
            case 2:
                monNeurone = new NeuroneSigmoide(nbEntrees);
                fichierSauvegarde = "cerveau_sigmoide.txt";
                nomFonction = "SIGMOIDE";
                Neurone.fixeCoefApprentissage(0.01f); 
                break;
                
            case 3:
                monNeurone = new NeuroneReLU(nbEntrees); 
                fichierSauvegarde = "cerveau_relu.txt";
                nomFonction = "RELU";
                Neurone.fixeCoefApprentissage(0.001f); 
                break;
                
            default:
                System.out.println("Erreur : Choix invalide ! Relancez le programme et tapez 1, 2 ou 3.");
                return;
        }

        System.out.println("\nConfiguration du neurone activée avec : " + nomFonction);
        System.out.println("Apprentissage en cours (tolérance à 5%)...");
        monNeurone.apprentissage(toutesLesEntrees, toutesLesConsignes, 0.05f); 
        monNeurone.sauvegarde(fichierSauvegarde);
        System.out.println("Modèle sauvegardé avec succès dans : " + fichierSauvegarde + "\n");


        // ---------------------------------------------------------------------
        // PARTIE 2 : EXAMEN ET EVALUATION (TEST)
        // ---------------------------------------------------------------------
        System.out.println("--- DÉBUT DE LA PHASE DE TEST ---");
        String dossierTest = "../../dataset_animaux/test/";
        List<String> cheminsTest = listeFichiers(dossierTest);
        
        if (cheminsTest == null || cheminsTest.isEmpty()) {
            System.out.println("Erreur : Aucune image de test trouvée.");
            return;
        }

        int nbImagesTest = cheminsTest.size();
        System.out.println(nbImagesTest + " images d'examen trouvées.");

        int bonnesReponses = 0;
        int vraisChats = 0, fauxChiens = 0;
        int vraisChiens = 0, fauxChats = 0;
        
        System.out.println("Évaluation globale en cours...");
        for (int i = 0; i < nbImagesTest; i++) {
            String path = cheminsTest.get(i);
            boolean estUnChat = path.contains("cat");
            float reponseAttendue = estUnChat ? (float)LabelChat : (float)LabelChien;

            Image img = new Image(path, estUnChat ? LabelChat : LabelChien, true);
            int[] pixelsBruts = img.donnees();
            float[] pixelsNormalises = new float[nbEntrees];
            
            for (int j = 0; j < nbEntrees; j++) {
                pixelsNormalises[j] = pixelsBruts[j] / 255.0f;
            }

            monNeurone.metAJour(pixelsNormalises);
            float predictionBrute = monNeurone.sortie();
            
            float predictionFinale = (predictionBrute >= 0.5f) ? (float)LabelChien : (float)LabelChat;

            // Matrice de confusion
            if (estUnChat) {
                if (predictionFinale == (float)LabelChat) vraisChats++;
                else fauxChiens++;
            } else {
                if (predictionFinale == (float)LabelChien) vraisChiens++;
                else fauxChats++;
            }

            if (predictionFinale == reponseAttendue) {
                bonnesReponses++;
            }
        }

        // Affichage des statistiques dynamiques
        System.out.println("\n--------------------------------------------------");
        System.out.println("RÉSULTATS DE L'EXAMEN FINAL (" + nomFonction + ")");
        System.out.println("--------------------------------------------------");
        System.out.println("Bonnes réponses : " + bonnesReponses + " / " + nbImagesTest);
        float precision = ((float) bonnesReponses / nbImagesTest) * 100;
        System.out.printf("PRÉCISION DU MODÈLE : %.2f %%\n", precision);

        System.out.println("\n--- MATRICE DE CONFUSION ---");
        System.out.println(" Le neurone a reconnu " + vraisChats + " vrais chats.");
        System.out.println(" Le neurone a reconnu " + vraisChiens + " vrais chiens.");
        System.out.println(" PIÈGES : Il a pris " + fauxChiens + " chats pour des chiens.");
        System.out.println(" PIÈGES : Il a pris " + fauxChats + " chiens pour des chats.");
        
        clavier.close(); // Bonne pratique : on ferme le clavier à la fin
    }
}