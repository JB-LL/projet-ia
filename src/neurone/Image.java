import java.io.*;
import java.util.*;
import javax.imageio.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.stream.*;
import java.awt.Color; 

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
        String type = estEnNiveauxDeGris() ? "grayscale" : " 3-canaux (RGB/TSL)";
        System.out.printf("Image (%s): label=%d, largeur=%d, hauteur=%d, taille=%d\n",
            type, label(), largeur(), hauteur(), taille());
    }

    public Image(final String cheminImage, int label, int modeCouleur) {
        try {
            final BufferedImage img = ImageIO.read(new File(cheminImage));
            this.label = label;
            largeur = img.getWidth(null);
            hauteur = img.getHeight(null);
            
            final int taille = (modeCouleur == 1) ? hauteur * largeur : 3 * hauteur * largeur;
            donnees = new int[taille];
            
            for (int i = 0; i < hauteur; ++i) {
                for (int j = 0; j < largeur; ++j) {
                    final long rgb = img.getRGB(j, i);
                    final int r = (int)((rgb>>16)&255); 
                    final int g = (int)((rgb>>8)&255);  
                    final int b = (int)((rgb)&255);     
                    final int index = i * largeur + j;
                    
                    if (modeCouleur == 1) { 
                        final float gris = 0.2125f * r + 0.7154f * g + 0.0721f * b; 
                        donnees[index] = (int) Math.max(0, Math.min(255, gris));
                    }
                    else if (modeCouleur == 2) { 
                        donnees[3*index+0] = r;
                        donnees[3*index+1] = g;
                        donnees[3*index+2] = b;
                    }
                    else if (modeCouleur == 3) { 
                        float[] tsl = Color.RGBtoHSB(r, g, b, null);
                        donnees[3*index+0] = (int)(tsl[0] * 255.0f); 
                        donnees[3*index+1] = (int)(tsl[1] * 255.0f); 
                        donnees[3*index+2] = (int)(tsl[2] * 255.0f); 
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.err.printf("Image non trouvee ou non lisible: %s\n", cheminImage);
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
        clavier.useLocale(Locale.US); 
        
        System.out.println("==================================================");
        System.out.println("  BIENVENUE DANS L'ENTRAINEMENT DE L'IA");
        System.out.println("==================================================");
        
        System.out.println(" Quelle fonction d'activation voulez-vous utiliser ?");
        System.out.println("  1 - HEAVISIDE (Basique, sortie binaire 0 ou 1)");
        System.out.println("  2 - SIGMOIDE  (Tres stable, bornee entre 0 et 1)");
        System.out.println("  3 - RELU      (Puissante mais risque d'explosion)");
        System.out.print("\n Votre choix (1, 2 ou 3) : ");
        int choixFonction = clavier.nextInt(); 

        // --- NOUVEAUTÉ : CHOIX DU NOMBRE D'IMAGES POUR RELU ---
        int limiteRelu = 2000; // Valeur par défaut de sécurité
        if (choixFonction == 3) {
            System.out.println("\n [OPTION RELU] Pour eviter une surcharge memoire, il est conseille de reduire le dataset.");
            System.out.print(" Combien d'images par classe (Chats/Chiens) voulez-vous utiliser ? (ex: 2000) : ");
            limiteRelu = clavier.nextInt();
        }

        float coefDefaut = 0.0f;
        if (choixFonction == 1) coefDefaut = 0.005f;
        else if (choixFonction == 2) coefDefaut = 0.01f;
        else if (choixFonction == 3) coefDefaut = 0.001f;

        System.out.println("\n Quel coefficient d'apprentissage voulez-vous utiliser ?");
        System.out.println("  (Tapez 0 pour utiliser la valeur par defaut : " + coefDefaut + ")");
        System.out.println("  (Ou tapez votre valeur personnalisee, ex: 0.1, 0.0001...)");
        System.out.print(" Votre choix : ");
        float saisieCoef = clavier.nextFloat();
        
        float coefFinal = (saisieCoef == 0.0f) ? coefDefaut : saisieCoef;

        System.out.println("\n Quel mode d'execution souhaitez-vous ?");
        System.out.println("  1 - Mode Classique (Details, Matrice de confusion, Sauvegarde)");
        System.out.println("  2 - Mode Export Excel (Sauvegarde dans un fichier a la fin)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixMode = clavier.nextInt();

        int nbRuns = 1;
        if (choixMode == 2) {
            System.out.print("\n Combien de tests (runs) voulez-vous generer pour Excel ? : ");
            nbRuns = clavier.nextInt();
        }
        
        System.out.println("\n Voulez-vous melanger les donnees d'entrainement (Aleatoire) ?");
        System.out.println("  1 - Oui (Recommande)");
        System.out.println("  2 - Non (Pour tester l'impact de l'ordre)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixAleatoire = clavier.nextInt();

        System.out.println("\n Voulez-vous normaliser les pixels (entre 0 et 1) ?");
        System.out.println("  1 - Oui (Recommande, division par 255)");
        System.out.println("  2 - Non (Garder les valeurs de 0 a 255)");
        System.out.print("\n Votre choix (1 ou 2) : ");
        int choixNormalisation = clavier.nextInt();

        System.out.println("\n Quel format de representation pour les images ?");
        System.out.println("  1 - Niveaux de gris (Noir et blanc)");
        System.out.println("  2 - RGB (Couleurs, 3x plus d'entrees)");
        System.out.println("  3 - TSL (Teinte, Saturation, Luminosite, 3x plus d'entrees)");
        System.out.print("\n Votre choix (1, 2 ou 3) : ");
        int choixCouleur = clavier.nextInt();

        System.out.println("==================================================\n");

        List<String> tousLesCheminsTrain = listeFichiers("../../dataset_animaux/train/");
        if (tousLesCheminsTrain == null || tousLesCheminsTrain.isEmpty()) {
            System.out.println("Erreur : Aucune image d'entrainement trouvee.");
            return;
        }

        // --- FILTRAGE AVEC LA LIMITE CHOISIE PAR L'UTILISATEUR ---
        List<String> cheminsTrain = new ArrayList<>();
        int maxImagesParClasse = (choixFonction == 3) ? limiteRelu : Integer.MAX_VALUE;
        int compteurChats = 0;
        int compteurChiens = 0;

        for (String chemin : tousLesCheminsTrain) {
            if (chemin.contains("cat") && compteurChats < maxImagesParClasse) {
                cheminsTrain.add(chemin);
                compteurChats++;
            } else if (chemin.contains("dog") && compteurChiens < maxImagesParClasse) {
                cheminsTrain.add(chemin);
                compteurChiens++;
            }
        }
        int nbImagesTrain = cheminsTrain.size();
        
        if (nbImagesTrain == 0) {
            System.out.println("Erreur : Aucune image trouvee pour cette configuration.");
            return;
        }

        Image premiereImage = new Image(cheminsTrain.get(0), LabelChat, choixCouleur);
        int nbEntrees = premiereImage.taille(); 

        String dossierTest = "../../dataset_animaux/test/";
        List<String> cheminsTest = listeFichiers(dossierTest);
        
        List<String> cheminsTestFiltres = new ArrayList<>();
        if (cheminsTest != null) {
            for (String chemin : cheminsTest) {
                if (chemin.contains("cat") || chemin.contains("dog")) {
                    cheminsTestFiltres.add(chemin);
                }
            }
        }
        int nbImagesTest = cheminsTestFiltres.size();

        // =========================================================================
        // MODE 1 : CLASSIQUE (MATRICE DE CONFUSION)
        // =========================================================================
        if (choixMode == 1) {
            System.out.println("--- DEBUT DE LA PHASE D'ENTRAINEMENT ---");
            System.out.println(nbImagesTrain + " images pretes pour l'entrainement.");
            if (choixFonction == 3) {
                System.out.println("(Limite de " + limiteRelu + " images par classe appliquee)");
            }
            
            if (choixAleatoire == 1) {
                Collections.shuffle(cheminsTrain); 
                System.out.println("Melange aleatoire active.");
            } else {
                System.out.println("Melange aleatoire DESACTIVE.");
            }

            float[][] toutesLesEntrees = new float[nbImagesTrain][nbEntrees];
            float[] toutesLesConsignes = new float[nbImagesTrain];

            System.out.println("Chargement des images d'entrainement...");
            for (int i = 0; i < nbImagesTrain; i++) {
                String chemin = cheminsTrain.get(i);
                boolean estUnChat = chemin.contains("cat");
                float consigne = estUnChat ? (float)LabelChat : (float)LabelChien;
                toutesLesConsignes[i] = consigne;
                
                Image img = new Image(chemin, estUnChat ? LabelChat : LabelChien, choixCouleur);
                int[] pixelsBruts = img.donnees();
                
                for (int j = 0; j < nbEntrees; j++) {
                    toutesLesEntrees[i][j] = (choixNormalisation == 1) ? (pixelsBruts[j] / 255.0f) : (float) pixelsBruts[j];
                }
            }

            iNeurone monNeurone = null;
            String fichierSauvegarde = "";
            String nomFonction = "";

            switch (choixFonction) {
                case 1: monNeurone = new NeuroneHeavyside(nbEntrees); fichierSauvegarde = "cerveau_heaviside.txt"; nomFonction = "HEAVISIDE"; break;
                case 2: monNeurone = new NeuroneSigmoide(nbEntrees); fichierSauvegarde = "cerveau_sigmoide.txt"; nomFonction = "SIGMOIDE"; break;
                case 3: monNeurone = new NeuroneReLU(nbEntrees); fichierSauvegarde = "cerveau_relu.txt"; nomFonction = "RELU"; break;
            }
            
            Neurone.fixeCoefApprentissage(coefFinal);

            System.out.println("\nConfiguration du neurone activee avec : " + nomFonction);
            System.out.println("Coefficient d'apprentissage applique : " + coefFinal);
            System.out.println("Apprentissage en cours (tolerance a 5%)...");
            
            monNeurone.apprentissage(toutesLesEntrees, toutesLesConsignes, 0.05f); 
            monNeurone.sauvegarde(fichierSauvegarde);
            System.out.println("Modele sauvegarde avec succes dans : " + fichierSauvegarde + "\n");

            System.out.println("--- DEBUT DE LA PHASE DE TEST ---");
            System.out.println(nbImagesTest + " images d'examen trouvees.");

            int bonnesReponses = 0;
            int vraisChats = 0, fauxChiens = 0;
            int vraisChiens = 0, fauxChats = 0;
            
            System.out.println("Evaluation globale en cours...");
            for (int i = 0; i < nbImagesTest; i++) {
                String path = cheminsTestFiltres.get(i);
                boolean estUnChat = path.contains("cat");
                float reponseAttendue = estUnChat ? (float)LabelChat : (float)LabelChien;

                Image img = new Image(path, estUnChat ? LabelChat : LabelChien, choixCouleur);
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
            System.out.println("RESULTATS DE L'EXAMEN FINAL (" + nomFonction + ")");
            System.out.println("--------------------------------------------------");
            System.out.println("Bonnes reponses : " + bonnesReponses + " / " + nbImagesTest);
            float precision = ((float) bonnesReponses / nbImagesTest) * 100;
            System.out.printf("PRECISION DU MODELE : %.2f %%\n", precision);

            System.out.println("\n--- MATRICE DE CONFUSION ---");
            System.out.println(" Le neurone a reconnu " + vraisChats + " vrais chats.");
            System.out.println(" Le neurone a reconnu " + vraisChiens + " vrais chiens.");
            System.out.println(" PIEGES : Il a pris " + fauxChiens + " chats pour des chiens.");
            System.out.println(" PIEGES : Il a pris " + fauxChats + " chiens pour des chats.");
        }
        
        // =========================================================================
        // MODE 2 : LA BOUCLE POUR EXCEL
        // =========================================================================
        else if (choixMode == 2) {
            System.out.println(" L'entrainement tourne. (Coefficient: " + coefFinal + ")");
            System.out.println(" Les logs du neurone vont s'afficher, c'est normal.");
            System.out.println(" Veuillez patienter jusqu'a la fin de la barre de progression virtuelle...\n");
            
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
                    boolean estUnChat = chemin.contains("cat");
                    float consigne = estUnChat ? (float)LabelChat : (float)LabelChien;
                    toutesLesConsignes[i] = consigne;
                    
                    Image img = new Image(chemin, estUnChat ? LabelChat : LabelChien, choixCouleur);
                    int[] pixelsBruts = img.donnees();
                    
                    for (int j = 0; j < nbEntrees; j++) {
                        toutesLesEntrees[i][j] = (choixNormalisation == 1) ? (pixelsBruts[j] / 255.0f) : (float) pixelsBruts[j];
                    }
                }

                iNeurone monNeurone = null;
                switch (choixFonction) {
                    case 1: monNeurone = new NeuroneHeavyside(nbEntrees); break;
                    case 2: monNeurone = new NeuroneSigmoide(nbEntrees); break;
                    case 3: monNeurone = new NeuroneReLU(nbEntrees); break;
                }

                Neurone.fixeCoefApprentissage(coefFinal);
                monNeurone.apprentissage(toutesLesEntrees, toutesLesConsignes, 0.05f); 

                int bonnesReponses = 0;
                for (int i = 0; i < nbImagesTest; i++) {
                    String path = cheminsTestFiltres.get(i);
                    boolean estUnChat = path.contains("cat");
                    float reponseAttendue = estUnChat ? (float)LabelChat : (float)LabelChien;

                    Image img = new Image(path, estUnChat ? LabelChat : LabelChien, choixCouleur);
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

            System.out.println("\n\n\n==================================================");
            System.out.println("  RESULTATS PRETS POUR EXCEL");
            System.out.println("==================================================");
            for (String ligne : resultatsPourExcel) {
                System.out.println(ligne);
            }
            System.out.println("==================================================");

            try {
                FileWriter writer = new FileWriter("resultats_excel.csv");
                for (String ligne : resultatsPourExcel) {
                    writer.write(ligne + "\n");
                }
                writer.close();
                System.out.println(" SUCCES : Un fichier 'resultats_excel.csv' a ete cree dans votre dossier !");
                System.out.println("   Vous pouvez l'ouvrir directement avec Excel.");
            } catch (IOException e) {
                System.out.println("Erreur lors de la creation du fichier CSV, utilisez le bloc ci-dessus.");
            }
        }

        clavier.close(); 
    }
}