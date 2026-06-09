import java.awt.Color;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.imageio.*;
 
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
 
    // --- ACCESSEURS ---
    public int label() {return label;}
    public int largeur() {return largeur;}
    public int hauteur() {return hauteur;}
    public int taille() {return donnees.length;}  // nombre de pixels: hauteur*largeur ou 3*hauteur*largeur pour une image RGB
    public int[] donnees() {return donnees;}
 
    public boolean estEnNiveauxDeGris() {return taille() == largeur() * hauteur();}
 
    public void afficheMetadonnees() {
        String type = estEnNiveauxDeGris() ? "grayscale" : " couleurs/TSL";
        System.out.printf("Image (%s): label=%d, largeur=%d, hauteur=%d, taille=%d\n",
            type, label(), largeur(), hauteur(), taille());
    }
 
    // --- CONSTRUCTEUR MODIFIÉ (GRIS, RGB, TSL) ---
    public Image(final String cheminImage, int label, int modeCouleur) {
        try {
            final BufferedImage img = ImageIO.read(new File(cheminImage));
            this.label = label;
            largeur = img.getWidth(null);
            hauteur = img.getHeight(null);
           
            // Si on est en gris, on a 1 valeur par pixel. En RGB/TSL, on a 3 canaux (R,G,B ou T,S,L)
            final int taille = (modeCouleur == 1) ? hauteur * largeur : 3 * hauteur * largeur;
            donnees = new int[taille];
           
            for (int i = 0; i < hauteur; ++i) {
                for (int j = 0; j < largeur; ++j) {
                    final long rgb = img.getRGB(j, i);
                    final int r = (int)((rgb>>16)&255); // Isoler la composante rouge
                    final int g = (int)((rgb>>8)&255);  // Isoler la composante verte
                    final int b = (int)((rgb)&255);  // Isoler la composante bleue  
                    final int index = i * largeur + j;
                   
                    if (modeCouleur == 1) { // 1 = GRIS
                        final float gris = 0.2125f * r + 0.7154f * g + 0.0721f * b;
                        donnees[index] = (int) Math.max(0, Math.min(255, gris));
                    }
                    else if (modeCouleur == 2) { // 2 = RGB
                        donnees[3*index+0] = r;
                        donnees[3*index+1] = g;
                        donnees[3*index+2] = b;
                    }
                    else if (modeCouleur == 3) { // 3 = TSL
                        float[] tsl = Color.RGBtoHSB(r, g, b, null);
                        // On multiplie par 255 pour garder la même échelle que le RGB/Gris
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
 
    // --- METHODE DE RECHERCHE DE FICHIERS ---
    public static List<String> listeFichiers(String repertoire) {
        List<String> cheminsFichiers = null;
        try {
            cheminsFichiers = Files.walk(Paths.get(repertoire)) // Récupère les chemins
                .filter(Files::isRegularFile)      // filtre uniquement les fichiers            
                .map(Path::toAbsolutePath)         // convertit le chemin en chemin absolu              
                .map(Path::toString)               // convertit le chemin en chaine de caractères             
                .collect(Collectors.toList());     // crée une collection à partir de ces chaînes            
        } catch (Exception e) {
            System.err.println("Dossier introuvable : " + repertoire);
        }
        return cheminsFichiers;
    }


    // Préparation des cibles pour nos experts
    // Un neurone expert (ex: expert Chat) doit s'activer (valoir 1) uniquement si c'est un chat, et valoir 0 pour le reste.
    // Cette fonction transforme notre liste globale de labels en une cible spécifique pour l'expert 'k' (chient, chat ou sauvage).
    public static float[] consignes(List<Integer> labels, int k) {
        float[] c = new float[labels.size()];
        for (int i = 0; i < labels.size(); i++)
            c[i] = labels.get(i) == k ? 1.0f : 0.0f; // 1 si c'est la bonne classe, 0 sinon
        return c;
    }

    // Algorithme de mélange : on mélange nos données d'entraînement pour éviter que le réseau n'apprenne par cœur un ordre précis (ex: d'abord tous les chats, puis tous les chiens), ce qui fausserait l'apprentissage.
    public static void melanger(float[][] entrees, List<Integer> labels, long graine) {
        Random rand = new Random(graine);
        for (int i = entrees.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            
            // Swap des entrées
            float[] tmpE = entrees[i]; 
            entrees[i] = entrees[j]; 
            entrees[j] = tmpE;
            
            // Swap des labels correspondant pour ne pas perdre l'association image/label
            int tmpL = labels.get(i); 
            labels.set(i, labels.get(j)); 
            labels.set(j, tmpL);
        }
    }

    // --- FONCTIONS D'AUGMENTATION DE DONNÉES ---
    
    // 1. Miroir : (Pour le réseau, un chat qui regarde à gauche ou à droite reste un chat. Ça rend le neurone plus robuste.)
    public static int[] creerMiroir(int[] pixels, int largeur, int hauteur, int modeCouleur) {
        int[] miroir = new int[pixels.length];
        int nbCanaux = (modeCouleur == 1) ? 1 : 3;
        for (int i = 0; i < hauteur; i++) {
            for (int j = 0; j < largeur; j++) {
                int idxOrig = (i * largeur + j) * nbCanaux;
                int idxDest = (i * largeur + (largeur - 1 - j)) * nbCanaux;
                for (int c = 0; c < nbCanaux; c++) {
                    miroir[idxDest + c] = pixels[idxOrig + c];
                }
            }
        }
        return miroir;
    }

    // 2. Égalisation d'histogramme: permet de mieux répartir la lumière et les contrastes sur l'image.
    // L'objectif est d'éviter que des photos trop sombres ou trop claires ne perturbent le neurone.
    public static void egaliserHistogramme(int[] pixels, int modeCouleur) {
        int nbCanaux = (modeCouleur == 1) ? 1 : 3;
        
        int canalDebut = (modeCouleur == 3) ? 2 : 0;
        int canalFin = (modeCouleur == 3) ? 3 : nbCanaux;

        for (int c = canalDebut; c < canalFin; c++) {
            int[] hist = new int[256];
            int total = 0;
            for (int i = c; i < pixels.length; i += nbCanaux) {
                int val = pixels[i];
                if (val >= 0 && val <= 255) { hist[val]++; total++; }
            }
            int[] cdf = new int[256];
            cdf[0] = hist[0];
            for (int i = 1; i < 256; i++) cdf[i] = cdf[i - 1] + hist[i];
            
            int cdfMin = 0;
            for (int i = 0; i < 256; i++) {
                if (cdf[i] > 0) { cdfMin = cdf[i]; break; }
            }
            if (total - cdfMin <= 0) continue;
            for (int i = c; i < pixels.length; i += nbCanaux) {
                int val = pixels[i];
                if (val >= 0 && val <= 255) {
                    pixels[i] = Math.round(((float)(cdf[val] - cdfMin) / (total - cdfMin)) * 255);
                }
            }
        }
    }

    // Chargement et Filtrage basé sur la logique textuelle globale (Avec Augmentation optionnelle)
    public static void chargerDonnees(String repertoire, int limiteParClasse, boolean estTrain, 
                                      boolean normaliser, int modeCouleur, 
                                      boolean appliquerMiroir, boolean appliquerEgalisation,
                                      List<float[]> entrees, List<Integer> labels) 
    {
        List<String> tousLesChemins = listeFichiers(repertoire);
        if (tousLesChemins == null || tousLesChemins.isEmpty()) return;
        int cptChats = 0, cptChiens = 0, cptSauvages = 0;
        for (String chemin : tousLesChemins) {
            int labelReel = LabelInconnu;
            
            if (chemin.contains("cat")) {
                if (estTrain && cptChats >= limiteParClasse) continue;
                labelReel = LabelChat;
                if (estTrain) cptChats++;
            } else if (chemin.contains("dog")) {
                if (estTrain && cptChiens >= limiteParClasse) continue;
                labelReel = LabelChien;
                if (estTrain) cptChiens++;
            } else if (chemin.contains("wild") || chemin.contains("sauvage")) {
                if (estTrain && cptSauvages >= limiteParClasse) continue;
                labelReel = LabelWild;
                if (estTrain) cptSauvages++;
            } else {
                continue;
            }

            Image img = new Image(chemin, labelReel, modeCouleur);
            if (img.donnees() == null) continue;
            
            int[] d = img.donnees();
            
            // Application de l'égalisation si demandée
            if (appliquerEgalisation) {
                egaliserHistogramme(d, modeCouleur);
            }
            
            // Préparation du vecteur d'entrée
            float[] entree = new float[d.length];
            for (int j = 0; j < d.length; j++) {
                // NORMALISATION : On divise par 255 pour ramener les valeurs entre 0 et 1.
                entree[j] = normaliser ? d[j] / 255.0f : (float) d[j];
            }
            entrees.add(entree);
            labels.add(labelReel);

            // Augmentation Miroir (On ne l'applique qu'à l'entraînement pour ne pas fausser le test final)
            if (estTrain && appliquerMiroir) {
                int[] dMiroir = creerMiroir(d, img.largeur(), img.hauteur(), modeCouleur);
                float[] entreeMiroir = new float[dMiroir.length];
                for (int j = 0; j < dMiroir.length; j++) {
                    entreeMiroir[j] = normaliser ? dMiroir[j] / 255.0f : (float) dMiroir[j];
                }
                entrees.add(entreeMiroir);
                labels.add(labelReel);
            }
        }
        if (estTrain) {
            System.out.printf("  Images lues : %d Chats, %d Chiens, %d Sauvages. (Total vecteurs générés : %d)\n", 
                cptChats, cptChiens, cptSauvages, entrees.size());
        } else {
            System.out.printf("  Images de test chargees : %d fichiers au total.\n", entrees.size());
        }
    }

    // Entraînement des experts avec critère MSE (Erreur Quadratique Moyenne)
    public static iNeurone[] entrainer(int choixFonction, int nbEntrees, float[][] toutesEntrees, List<Integer> labels, float eta) {
        String nomFonction = choixFonction == 1 ? "HEAVISIDE" : choixFonction == 2 ? "SIGMOIDE" : "RELU";
        System.out.println("\n--- ENTRAINEMENT (" + nomFonction + ") ---");
        // On règle le pas d'apprentissage (learning rate). S'il est trop grand, l'algo va sauter au-dessus de la solution idéale. S'il est trop petit, l'apprentissage sera très lent.
        Neurone.fixeCoefApprentissage(eta);
        //On crée 3 neurones experts, [0] = Expert Chat, [1] = Expert Chien, [2] = Expert Sauvage
        iNeurone[] neurones = new iNeurone[3];
        for (int k = 0; k < 3; k++) {
            switch (choixFonction) {
                case 1: neurones[k] = new NeuroneHeavyside(nbEntrees); break;
                case 2: neurones[k] = new NeuroneSigmoide(nbEntrees);  break;
                case 3: neurones[k] = new NeuroneReLU(nbEntrees);      break;
                default: neurones[k] = new NeuroneSigmoide(nbEntrees);
            }
        }
        // On entraîne chaque expert indépendamment. L'apprentissage s'arrête quand l'erreur (MSE) devient inférieure à 0.05f.
        System.out.println("  Entrainement de l'Expert CHAT...");
        neurones[0].apprentissage(toutesEntrees, consignes(labels, LabelChat), 0.10f);
        System.out.println("  Entrainement de l'Expert CHIEN...");
        neurones[1].apprentissage(toutesEntrees, consignes(labels, LabelChien), 0.10f);
        System.out.println("  Entrainement de l'Expert SAUVAGE...");
        neurones[2].apprentissage(toutesEntrees, consignes(labels, LabelWild), 0.10f);
        return neurones;
    }

    // Évaluation : On teste nos 3 experts sur les images de test jamais vues pendant l'entraînement.
    public static float evaluer(iNeurone[] neurones, List<float[]> testEntrees, List<Integer> testLabels, boolean afficherResultat) {
        int correct = 0;
        for (int i = 0; i < testEntrees.size(); i++) {
            neurones[0].metAJour(testEntrees.get(i)); float scoreChat = neurones[0].sortie();
            neurones[1].metAJour(testEntrees.get(i)); float scoreChien = neurones[1].sortie();
            neurones[2].metAJour(testEntrees.get(i)); float scoreSauvage = neurones[2].sortie();
            // On regarde quel expert a le score le plus haut. C'est lui qui détermine la prédiction finale de notre système.
            int prediction = LabelChat;
            float maxScore = scoreChat;
            if (scoreChien > maxScore) { maxScore = scoreChien; prediction = LabelChien; }
            if (scoreSauvage > maxScore) { maxScore = scoreSauvage; prediction = LabelWild; }
            int labelReel = testLabels.get(i);
            if (prediction == labelReel) correct++;
        }
        float precision = (float) correct / testEntrees.size() * 100;

        if (afficherResultat) {
            System.out.println("\n--------------------------------------------------");
            System.out.printf("PRECISION GLOBALE DU MODELE : %.2f %%\n", precision);
            System.out.println("--------------------------------------------------");
        }
        return precision;
    }
 
    // --- LE PROGRAMME PRINCIPAL ---
    public static void main (String[] args)
    {
        Scanner clavier = new Scanner(System.in);
        clavier.useLocale(Locale.US);
        System.out.println("==================================================");
        System.out.println("  PROJET IA : CHATS vs CHIENS vs SAUVAGES");
        System.out.println("==================================================");

        System.out.println(" Quelle fonction d'activation ?");
        System.out.println("  1 - HEAVISIDE");
        System.out.println("  2 - SIGMOIDE");
        System.out.println("  3 - RELU");
        System.out.print(" Votre choix (1, 2 ou 3) : ");
        int choixFonction = clavier.nextInt();
 
        System.out.print("\n Combien d'images MAX par classe au depart ? (Ex: 2000) : ");
        int limiteImages = clavier.nextInt();
 
        float coefDefaut = (choixFonction == 1) ? 0.005f : (choixFonction == 2) ? 0.001f : 0.001f;
        System.out.println("\n Quel coefficient d'apprentissage ? (Tapez 0 pour le defaut : " + coefDefaut + ") : ");
        float saisieCoef = clavier.nextFloat();
        float coefFinal = (saisieCoef == 0.0f) ? coefDefaut : saisieCoef;
 
        System.out.println("\n Quel mode d'execution ?");
        System.out.println("  1 - Mode Classique (Calcul du score)");
        System.out.println("  2 - Mode Export Excel (Plusieurs runs)");
        System.out.print(" Votre choix (1 ou 2) : ");
        int choixMode = clavier.nextInt();
 
        int nbRuns = 1;
        if (choixMode == 2) {
            System.out.print(" Combien de runs pour Excel ? : ");
            nbRuns = clavier.nextInt();
        }
       
        System.out.print("\n Melanger les donnees aleatoirement ? (1=Oui, 2=Non) : ");
        int choixAleatoire = clavier.nextInt();
 
        System.out.print(" Normaliser les pixels entre 0 et 1 ? (1=Oui, 2=Non) : ");
        int choixNormalisation = clavier.nextInt();
 
        System.out.println("\n Format d'image ?");
        System.out.println("  1 - Niveaux de gris");
        System.out.println("  2 - RGB (Couleurs)");
        System.out.println("  3 - TSL (Teinte, Saturation, Luminosite)");
        System.out.print(" Votre choix (1, 2 ou 3) : ");
        int choixCouleur = clavier.nextInt();
        boolean normaliser = (choixNormalisation == 1);
        
        // --- MENUS AUGMENTATION DE DONNEES ---
        System.out.print("\n Activer l'augmentation par Effet Miroir ? (1=Oui, 2=Non) : ");
        boolean appliquerMiroir = (clavier.nextInt() == 1);
        System.out.print(" Activer l'Égalisation d'histogramme ? (1=Oui, 2=Non) : ");
        boolean appliquerEgalisation = (clavier.nextInt() == 1);
        System.out.println("==================================================\n");
 
        // Chemins d'accès relatifs
        String baseTrain = "../../dataset_animaux/train/";
        String baseTest = "../../dataset_animaux/test/";
 
        // --- LECTURE DU DOSSIER TEST ---
        System.out.println("--- CHARGEMENT TEST ---");
        List<float[]> testEntrees = new ArrayList<>();
        List<Integer> testLabels  = new ArrayList<>();
        // On n'applique JAMAIS le miroir sur le dataset de test pour ne pas fausser l'évaluation !
        chargerDonnees(baseTest, Integer.MAX_VALUE, false, normaliser, choixCouleur, false, appliquerEgalisation, testEntrees, testLabels);
 
        // =========================================================================
        // MODE 1 : CLASSIQUE
        // =========================================================================
        if (choixMode == 1) {
            System.out.println("\n--- CHARGEMENT TRAIN ---");
            List<float[]> trainEntrees = new ArrayList<>();
            List<Integer> trainLabels  = new ArrayList<>();
            chargerDonnees(baseTrain, limiteImages, true, normaliser, choixCouleur, appliquerMiroir, appliquerEgalisation, trainEntrees, trainLabels);

            if (trainEntrees.isEmpty()) {
                System.out.println("Erreur : Aucune image d'entrainement chargee.");
                clavier.close();
                return;
            }

            int nbEntrees = trainEntrees.get(0).length;
            // Conversion de la liste vers un tableau 2D pour respecter la signature de apprentissage() de iNeurone
            float[][] toutesEntrees = trainEntrees.toArray(new float[0][]);
 
            if (choixAleatoire == 1) {
                melanger(toutesEntrees, trainLabels, 42L);
            }
 
            // Entraînement
            iNeurone[] neurones = entrainer(choixFonction, nbEntrees, toutesEntrees, trainLabels, coefFinal);
            // Évaluation (Calcul de la précision sur les données inconnues)
            System.out.println("\n--- DEBUT DU TEST SUR " + testEntrees.size() + " IMAGES ---");
            evaluer(neurones, testEntrees, testLabels, true);
        }
       
        // =========================================================================
        // MODE 2 : EXPORT EXCEL
        // =========================================================================
        else if (choixMode == 2) {
            System.out.println(" Les 3 experts s'entrainent " + nbRuns + " fois. Veuillez patienter...\n");
            List<String> resultatsPourExcel = new ArrayList<>();
            resultatsPourExcel.add("Run;Precision");
 
            for (int run = 1; run <= nbRuns; run++) {
                System.out.println("\n=== RUN " + run + " / " + nbRuns + " ===");
                
                List<float[]> trainEntrees = new ArrayList<>();
                List<Integer> trainLabels  = new ArrayList<>();
                chargerDonnees(baseTrain, limiteImages, true, normaliser, choixCouleur, appliquerMiroir, appliquerEgalisation, trainEntrees, trainLabels);
 
                int nbEntrees = trainEntrees.get(0).length;
                float[][] toutesEntrees = trainEntrees.toArray(new float[0][]);
 
                if (choixAleatoire == 1) {
                    // On change la graine à chaque run pour avoir un mélange différent et voir la robustesse
                    melanger(toutesEntrees, trainLabels, new Random().nextLong());
                }
 
                iNeurone[] neurones = entrainer(choixFonction, nbEntrees, toutesEntrees, trainLabels, coefFinal);
                float precision = evaluer(neurones, testEntrees, testLabels, false);
                
                System.out.printf("Run %d : %.2f%%\n", run, precision);
                resultatsPourExcel.add(String.format(Locale.FRENCH, "%d;%.2f", run, precision));
            }
 
            System.out.println("\n==================================================");
            System.out.println("  RÉSULTATS POUR EXCEL");
            System.out.println("==================================================");
            for (String ligne : resultatsPourExcel) {
                System.out.println(ligne);
            }
 
            try {
                // Création du fichier CSV pour exploiter les résultats (générer des graphes pour le rapport par ex)
                FileWriter writer = new FileWriter("resultats_excel.csv");
                for (String ligne : resultatsPourExcel) writer.write(ligne + "\n");
                writer.close();
                System.out.println("\nFichier 'resultats_excel.csv' cree avec succes !");
            } catch (IOException e) { 
                System.out.println("Erreur lors de la creation du fichier CSV.");
            }
        }
 
        clavier.close();
    }
}