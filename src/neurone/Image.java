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
    private int label = -1;
    private int largeur = 0;
    private int hauteur = 0;
    private int[] donnees = null; 

    public int label() {return label;}
    public int largeur() {return largeur;}
    public int hauteur() {return hauteur;}
    public int taille() {return donnees.length;} 
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
                    final int r = (int)((rgb>>16)&255); 
                    final int g = (int)((rgb>>8)&255);  
                    final int b = (int)((rgb)&255);     
                    final int index = i * largeur + j;
                    if (niveauxDeGris) {
                        final float gris = 0.2125f * r + 0.7154f * g + 0.0721f * b; 
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

    public static void main (String[] args)
    {
        Scanner clavier = new Scanner(System.in);
        
        System.out.println("==================================================");
        System.out.println("  BIENVENUE DANS L'ENTRAÎNEMENT DE L'IA");
        System.out.println("==================================================");
        
        System.out.println(" Quelle fonction d'activation voulez-vous utiliser ?");
        System.out.println("  1 - HEAVISIDE (Basique, sortie binaire 0 ou 1)");
        System.out.println("  2 - SIGMOIDE  (Très stable, bornée entre 0 et 1)");
        System.out.println("  3 - RELU      (Puissante mais risque d'explosion)");
        System.out.print("\n Votre choix (1, 2 ou 3) : ");
        int choixFonction = clavier.nextInt(); 

        System.out.println("\n Quel mode d'exécution souhaitez-vous ?");
        System.out.println("  1 - Mode Classique (Détails, Matrice de confusion, Sauvegarde)");
        System.out.println("  2 - Mode Export Excel (Sauvegarde dans un fichier à la fin)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixMode = clavier.nextInt();

        int nbRuns = 1;
        if (choixMode == 2) {
            System.out.print("\n Combien de tests (runs) voulez-vous générer pour Excel ? : ");
            nbRuns = clavier.nextInt();
        }
        
        System.out.println("\n Voulez-vous mélanger les données d'entraînement (Aléatoire) ?");
        System.out.println("  1 - Oui (Recommandé)");
        System.out.println("  2 - Non (Pour tester l'impact de l'ordre)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixAleatoire = clavier.nextInt();

        System.out.println("\n Voulez-vous normaliser les pixels (entre 0 et 1) ?");
        System.out.println("  1 - Oui (Recommandé, division par 255)");
        System.out.println("  2 - Non (Garder les valeurs de 0 à 255)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixNormalisation = clavier.nextInt();

        System.out.println("\n Quel format de représentation pour les images ?");
        System.out.println("  1 - Niveaux de gris (Noir et blanc)");
        System.out.println("  2 - RGB (Couleurs, 3x plus d'entrées)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixCouleur = clavier.nextInt();
        boolean modeGris = (choixCouleur == 1);

        System.out.println("==================================================\n");

        // --- PRÉPARATION (Commune aux deux modes) ---
        List<String> tousLesCheminsTrain = listeFichiers("../../dataset_animaux/train/");
        if (tousLesCheminsTrain == null || tousLesCheminsTrain.isEmpty()) {
            System.out.println("Erreur : Aucune image d'entraînement trouvée.");
            return;
        }

        List<String> cheminsTrain = new ArrayList<>();
        for (String chemin : tousLesCheminsTrain) {
            if (chemin.contains("cat") || chemin.contains("dog")) {
                cheminsTrain.add(chemin);
            }
        }
        int nbImagesTrain = cheminsTrain.size();
        
        // Initialisation de la première image pour récupérer la taille du réseau
        Image premiereImage = new Image(cheminsTrain.get(0), LabelChat, modeGris);
        int nbEntrees = premiereImage.taille(); 

        String dossierTest = "../../dataset_animaux/test/";
        List<String> cheminsTest = listeFichiers(dossierTest);
        
        // On filtre aussi le dossier test au cas où des intrus s'y trouvent
        List<String> cheminsTestFiltres = new ArrayList<>();
        for (String chemin : cheminsTest) {
            if (chemin.contains("cat") || chemin.contains("dog")) {
                cheminsTestFiltres.add(chemin);
            }
        }
        int nbImagesTest = cheminsTestFiltres.size();

        // =========================================================================
        // MODE 1 : CLASSIQUE (MATRICE DE CONFUSION)
        // =========================================================================
        if (choixMode == 1) {
            System.out.println("--- DÉBUT DE LA PHASE D'ENTRAÎNEMENT ---");
            System.out.println(nbImagesTrain + " images de chats/chiens prêtes pour l'entraînement.");
            
            if (choixAleatoire == 1) {
                Collections.shuffle(cheminsTrain); 
                System.out.println("Mélange aléatoire activé.");
            } else {
                System.out.println("Mélange aléatoire DÉSACTIVÉ.");
            }

            float[][] toutesLesEntrees = new float[nbImagesTrain][nbEntrees];
            float[] toutesLesConsignes = new float[nbImagesTrain];

            System.out.println("Chargement des images d'entraînement...");
            for (int i = 0; i < nbImagesTrain; i++) {
                String chemin = cheminsTrain.get(i);
                float consigne = chemin.contains("cat") ? (float)LabelChat : (float)LabelChien;
                toutesLesConsignes[i] = consigne;
                Image img = new Image(chemin, chemin.contains("cat") ? LabelChat : LabelChien, modeGris);
                int[] pixelsBruts = img.donnees();
                
                for (int j = 0; j < nbEntrees; j++) {
                    toutesLesEntrees[i][j] = (choixNormalisation == 1) ? (pixelsBruts[j] / 255.0f) : (float) pixelsBruts[j];
                }
            }

            iNeurone monNeurone = null;
            String fichierSauvegarde = "";
            String nomFonction = "";

            switch (choixFonction) {
                case 1: monNeurone = new NeuroneHeavyside(nbEntrees); fichierSauvegarde = "cerveau_heaviside.txt"; nomFonction = "HEAVISIDE"; Neurone.fixeCoefApprentissage(0.005f); break;
                case 2: monNeurone = new NeuroneSigmoide(nbEntrees); fichierSauvegarde = "cerveau_sigmoide.txt"; nomFonction = "SIGMOIDE"; Neurone.fixeCoefApprentissage(0.01f); break;
                case 3: monNeurone = new NeuroneReLU(nbEntrees); fichierSauvegarde = "cerveau_relu.txt"; nomFonction = "RELU"; Neurone.fixeCoefApprentissage(0.001f); break;
            }

            System.out.println("\nConfiguration du neurone activée avec : " + nomFonction);
            System.out.println("Apprentissage en cours (tolérance à 5%)...");
            monNeurone.apprentissage(toutesLesEntrees, toutesLesConsignes, 0.05f); 
            monNeurone.sauvegarde(fichierSauvegarde);
            System.out.println("Modèle sauvegardé avec succès dans : " + fichierSauvegarde + "\n");

            System.out.println("--- DÉBUT DE LA PHASE DE TEST ---");
            System.out.println(nbImagesTest + " images d'examen trouvées.");

            int bonnesReponses = 0;
            int vraisChats = 0, fauxChiens = 0;
            int vraisChiens = 0, fauxChats = 0;
            
            System.out.println("Évaluation globale en cours...");
            for (int i = 0; i < nbImagesTest; i++) {
                String path = cheminsTestFiltres.get(i);
                boolean estUnChat = path.contains("cat");
                float reponseAttendue = estUnChat ? (float)LabelChat : (float)LabelChien;

                Image img = new Image(path, estUnChat ? LabelChat : LabelChien, modeGris);
                int[] pixelsBruts = img.donnees();
                float[] pixelsNormalises = new float[nbEntrees];
                
                for (int j = 0; j < nbEntrees; j++) {
                    pixelsNormalises[j] = (choixNormalisation == 1) ? (pixelsBruts[j] / 255.0f) : (float) pixelsBruts[j];
                }

                monNeurone.metAJour(pixelsNormalises);
                float predictionBrute = monNeurone.sortie();
                float predictionFinale = (predictionBrute >= 0.5f) ? (float)LabelChien : (float)LabelChat;

                if (estUnChat) {
                    if (predictionFinale == (float)LabelChat) vraisChats++; else fauxChiens++;
                } else {
                    if (predictionFinale == (float)LabelChien) vraisChiens++; else fauxChats++;
                }
                if (predictionFinale == reponseAttendue) { bonnesReponses++; }
            }

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
        }
        
        // =========================================================================
        // MODE 2 : LA BOUCLE POUR EXCEL
        // =========================================================================
        else if (choixMode == 2) {
            System.out.println(" L'entraînement tourne. Les logs du neurone vont s'afficher, c'est normal.");
            System.out.println("Veuillez patienter jusqu'à la fin de la barre de progression virtuelle...\n");
            
            List<String> resultatsPourExcel = new ArrayList<>();
            resultatsPourExcel.add("Run;Precision"); 

            for (int run = 1; run <= nbRuns; run++) {
                
                if (choixAleatoire == 1) {
                    Collections.shuffle(cheminsTrain); 
                }
                
                float[][] toutesLesEntrees = new float[nbImagesTrain][nbEntrees];
                float[] toutesLesConsignes = new float[nbImagesTrain];

                for (int i = 0; i < nbImagesTrain; i++) {
                    String chemin = cheminsTrain.get(i);
                    float consigne = chemin.contains("cat") ? (float)LabelChat : (float)LabelChien;
                    toutesLesConsignes[i] = consigne;
                    Image img = new Image(chemin, chemin.contains("cat") ? LabelChat : LabelChien, modeGris);
                    int[] pixelsBruts = img.donnees();
                    
                    for (int j = 0; j < nbEntrees; j++) {
                        toutesLesEntrees[i][j] = (choixNormalisation == 1) ? (pixelsBruts[j] / 255.0f) : (float) pixelsBruts[j];
                    }
                }

                iNeurone monNeurone = null;
                switch (choixFonction) {
                    case 1: monNeurone = new NeuroneHeavyside(nbEntrees); Neurone.fixeCoefApprentissage(0.005f); break;
                    case 2: monNeurone = new NeuroneSigmoide(nbEntrees); Neurone.fixeCoefApprentissage(0.01f); break;
                    case 3: monNeurone = new NeuroneReLU(nbEntrees); Neurone.fixeCoefApprentissage(0.001f); break;
                }

                monNeurone.apprentissage(toutesLesEntrees, toutesLesConsignes, 0.05f); 

                int bonnesReponses = 0;
                for (int i = 0; i < nbImagesTest; i++) {
                    String path = cheminsTestFiltres.get(i);
                    boolean estUnChat = path.contains("cat");
                    float reponseAttendue = estUnChat ? (float)LabelChat : (float)LabelChien;

                    Image img = new Image(path, estUnChat ? LabelChat : LabelChien, modeGris);
                    int[] pixelsBruts = img.donnees();
                    float[] pixelsNormalises = new float[nbEntrees];
                    
                    for (int j = 0; j < nbEntrees; j++) {
                        pixelsNormalises[j] = (choixNormalisation == 1) ? (pixelsBruts[j] / 255.0f) : (float) pixelsBruts[j];
                    }

                    monNeurone.metAJour(pixelsNormalises);
                    float predictionBrute = monNeurone.sortie();
                    float predictionFinale = (predictionBrute >= 0.5f) ? (float)LabelChien : (float)LabelChat;

                    if (predictionFinale == reponseAttendue) {
                        bonnesReponses++;
                    }
                }

                float precision = ((float) bonnesReponses / nbImagesTest) * 100;
                String ligne = String.format(Locale.FRENCH, "%d;%.2f", run, precision);
                resultatsPourExcel.add(ligne);
            }

            // --- AFFICHAGE FINAL EN BLOC PROPRE ---
            System.out.println("\n\n\n==================================================");
            System.out.println("  RÉSULTATS PRÊTS POUR EXCEL");
            System.out.println("==================================================");
            for (String ligne : resultatsPourExcel) {
                System.out.println(ligne);
            }
            System.out.println("==================================================");

            // --- CRÉATION DU FICHIER AUTOMATIQUE ---
            try {
                FileWriter writer = new FileWriter("resultats_excel.csv");
                for (String ligne : resultatsPourExcel) {
                    writer.write(ligne + "\n");
                }
                writer.close();
                System.out.println(" SUCCÈS : Un fichier 'resultats_excel.csv' a été créé dans votre dossier !");
                System.out.println("   Vous pouvez l'ouvrir directement avec Excel.");
            } catch (IOException e) {
                System.out.println("Erreur lors de la création du fichier CSV, utilisez le bloc ci-dessus.");
            }
        }

        clavier.close(); 
    }
}