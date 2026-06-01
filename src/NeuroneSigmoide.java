public class NeuroneSigmoide extends Neurone
{
    // Constructeur : on transmet simplement le nombre d'entrées à la classe mère
    public NeuroneSigmoide(final int nbEntrees) 
    {
        super(nbEntrees);
    }

    // Redéfinition de la fonction d'activation avec la formule de la sigmoïde
    @Override
    protected float activation(final float valeur) 
    {
        return (float) (1.0 / (1.0 + Math.exp(-valeur)));
    }
}