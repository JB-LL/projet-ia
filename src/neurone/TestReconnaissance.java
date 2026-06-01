import java.util.List;

public class TestReconnaissance {
    public static void main(String[] args) {
        
        // =========================================================
        // 1. CONFIGURATION : QUEL CERVEAU VEUX-TU TESTER ?
        // =========================================================
        String choixFonction = "SIGMOIDE"; 
        
        String dossierTest = "../../dataset_animaux/test/";
        System.out.println("Recherche des images de test dans : " + dossierTest);
        List<String> chemins = Image.listeFichiers(dossierTest);
        
        if (chemins == null || chemins.isEmpty()) {
            System.out.println("Erreur : Aucune image trouvée dans le dossier de test !");
            return;
        }

        int nbImages = chemins.size();
        System.out.println(nbImages + " images d'examen trouvées.");

        Image imgTest = new Image(chemins.get(0), 0, true);
        int nbPixels = imgTest.taille();
        
        // =========================================================
        // 2. CRÉATION DYNAMIQUE ET CHARGEMENT DU CERVEAU
        // =========================================================
        iNeurone monNeurone = null;
        String fichierACHarger = "";

        switch (choixFonction) {
            case "HEAVISIDE":
                monNeurone = new NeuroneHeavyside(nbPixels);
                fichierACHarger = "cerveau_heaviside.txt";
                break;
            case "SIGMOIDE":
                monNeurone = new NeuroneSigmoide(nbPixels);
                fichierACHarger = "cerveau_sigmoide.txt";
                break;
            case "RELU":
                monNeurone = new NeuroneReLU(nbPixels);
                fichierACHarger = "cerveau_relu.txt";
                break;
            default:
                System.out.println("Erreur : Fonction inconnue !");
                return;
        }

        System.out.println("Chargement du cerveau depuis : " + fichierACHarger + " ...");
        ((Neurone) monNeurone).chargement(fichierACHarger);

        // Variables de statistiques
        int bonnesReponses = 0;
        int erreurs = 0;
        
        // Variables pour la Matrice de Confusion
        int vraisChats = 0, fauxChiens = 0;
        int vraisChiens = 0, fauxChats = 0;

        // =========================================================
        // 3. L'EXAMEN FINAL
        // =========================================================
        System.out.println("Début de l'évaluation globale...\n");
        
        for (int i = 0; i < nbImages; i++) {
            String path = chemins.get(i);
            
            boolean estUnChat = path.contains("cat");
            // NOUVELLE REGLE : Chat = 0.0f, Chien = 1.0f
            float reponseAttendue = estUnChat ? 0.0f : 1.0f;

            Image img = new Image(path, estUnChat ? 0 : 1, true);
            int[] pixelsBruts = img.donnees();
            float[] pixelsNormalises = new float[nbPixels];
            
            for (int j = 0; j < nbPixels; j++) {
                pixelsNormalises[j] = pixelsBruts[j] / 255.0f;
            }

            monNeurone.metAJour(pixelsNormalises);
            float predictionBrute = monNeurone.sortie();

            // Si >= 0.5 on dit "Chien" (1.0f), sinon on dit "Chat" (0.0f)
            float predictionFinale = (predictionBrute >= 0.5f) ? 1.0f : 0.0f;

            // --- MATRICE DE CONFUSION ---
            if (estUnChat) {
                if (predictionFinale == 0.0f) vraisChats++;
                else fauxChiens++;
            } else {
                if (predictionFinale == 1.0f) vraisChiens++;
                else fauxChats++;
            }

            // --- VÉRIFICATION VISUELLE POUR LES 20 PREMIÈRES IMAGES ---
            if (i < 20) {
                String vraiAnimal = estUnChat ? "Chat " : "Chien";
                // CORRECTION ICI : 0.0f = Chat, 1.0f = Chien
                String animalPredit = (predictionFinale == 0.0f) ? "Chat " : "Chien";
                String resultat = (predictionFinale == reponseAttendue) ? " SUCCÈS" : " ERREUR";
                
                System.out.printf("Fichier: %-45s | Réalité: %s | Le neurone dit: %s (Confiance: %.2f) -> %s\n", 
                                  path.substring(Math.max(0, path.length() - 25)), vraiAnimal, animalPredit, predictionBrute, resultat);
            }
            // -----------------------------------------------------------

            if (predictionFinale == reponseAttendue) {
                bonnesReponses++;
            } else {
                erreurs++;
            }
        }

        System.out.println("\n--------------------------------------------------");
        System.out.println("RÉSULTATS DE L'EXAMEN FINAL : " + choixFonction);
        System.out.println("--------------------------------------------------");
        System.out.println("Bonnes réponses : " + bonnesReponses);
        System.out.println("Erreurs         : " + erreurs);
        
        float precision = ((float) bonnesReponses / nbImages) * 100;
        System.out.printf("PRÉCISION DU MODÈLE : %.2f %%\n", precision);

        System.out.println("\n--- MATRICE DE CONFUSION ---");
        System.out.println(" Le neurone a reconnu " + vraisChats + " vrais chats.");
        System.out.println(" Le neurone a reconnu " + vraisChiens + " vrais chiens.");
        System.out.println(" PIÈGES : Il a pris " + fauxChiens + " chats pour des chiens.");
        System.out.println(" PIÈGES : Il a pris " + fauxChats + " chiens pour des chats.");
    }
}