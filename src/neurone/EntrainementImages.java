import java.util.List;
import java.util.Collections;
import java.util.ArrayList; // N'oublie pas l'import pour le filtre !

public class EntrainementImages {
    public static void main(String[] args) {
        
        // =========================================================
        // 1. CONFIGURATION : CHOISIS TA FONCTION D'ACTIVATION ICI
        // =========================================================
        String choixFonction = "SIGMOIDE"; 
        
        System.out.println("Recherche des images dans le dossier ../../dataset_animaux/train/ ...");
        List<String> tousLesChemins = Image.listeFichiers("../../dataset_animaux/train/");
        
        if (tousLesChemins == null || tousLesChemins.isEmpty()) {
            System.out.println("Erreur : Aucune image trouvée.");
            return;
        }

        // Filtre pour enlever les lions (si le dossier existe encore)
        List<String> chemins = new ArrayList<>();
        for (String chemin : tousLesChemins) {
            if (chemin.contains("cat") || chemin.contains("dog")) {
                chemins.add(chemin);
            }
        }

        Collections.shuffle(chemins); 
        int nbImages = chemins.size();
        System.out.println("Génial ! " + nbImages + " images de chats et chiens trouvées.");

        Image premiereImage = new Image(chemins.get(0), 0, true);
        int nbEntrees = premiereImage.taille(); 
        
        float[][] toutesLesEntrees = new float[nbImages][nbEntrees];
        float[] toutesLesConsignes = new float[nbImages];

        System.out.println("Chargement et normalisation des images en cours...");

        for (int i = 0; i < nbImages; i++) {
            String chemin = chemins.get(i);
            
            // =========================================================
            // MODIFICATION ICI : HARMONISATION DES LABELS
            // Si c'est un Chat, on met 0.0f. Si c'est un Chien, on met 1.0f.
            // =========================================================
            float consigne = chemin.contains("cat") ? 0.0f : 1.0f;
            toutesLesConsignes[i] = consigne;

            int labelPourImage = chemin.contains("cat") ? 0 : 1; 
            Image img = new Image(chemin, labelPourImage, true);
            int[] pixelsBruts = img.donnees();

            for (int j = 0; j < nbEntrees; j++) {
                toutesLesEntrees[i][j] = pixelsBruts[j] / 255.0f;
            }
        }

        // =========================================================
        // 2. CRÉATION DYNAMIQUE DU NEURONE SELON LE CHOIX
        // =========================================================
        iNeurone monNeurone = null;
        String fichierSauvegarde = "";

        System.out.println("\nConfiguration du neurone avec : " + choixFonction);

        switch (choixFonction) {
            case "HEAVISIDE":
                monNeurone = new NeuroneHeavyside(nbEntrees);
                fichierSauvegarde = "cerveau_heaviside.txt";
                Neurone.fixeCoefApprentissage(0.005f); 
                break;
                
            case "SIGMOIDE":
                monNeurone = new NeuroneSigmoide(nbEntrees);
                fichierSauvegarde = "cerveau_sigmoide.txt";
                Neurone.fixeCoefApprentissage(0.01f); 
                break;
                
            case "RELU":
                monNeurone = new NeuroneReLU(nbEntrees); 
                fichierSauvegarde = "cerveau_relu.txt";
                Neurone.fixeCoefApprentissage(0.001f); 
                break;
                
            default:
                System.out.println("Erreur : Fonction inconnue !");
                return;
        }

        // =========================================================
        // 3. APPRENTISSAGE ET SAUVEGARDE
        // =========================================================
        System.out.println("Début de l'apprentissage (tolérance d'erreur à 5%)...");
        monNeurone.apprentissage(toutesLesEntrees, toutesLesConsignes, 0.05f); 

        ((Neurone) monNeurone).sauvegarde(fichierSauvegarde);
        System.out.println("\nEntrainement termine ! Modele sauvegarde dans : " + fichierSauvegarde);
    }
}