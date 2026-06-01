public class NeuroneReLU extends Neurone
{
    // Constructeur : transmet le nombre d'entrées au constructeur de Neurone
    public NeuroneReLU(final int nbEntrees) 
    {
        super(nbEntrees);
    }

    // Redéfinition de la fonction d'activation pour la ReLU
    @Override
    protected float activation(final float valeur) 
    {
        // Renvoie 0 si la valeur est négative, sinon renvoie la valeur elle-même
        return Math.max(0.0f, valeur);
    }
}