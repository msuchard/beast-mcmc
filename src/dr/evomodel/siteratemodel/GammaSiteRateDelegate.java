/*
 * GammaSiteRateDelegate.java
 *
 * Copyright © 2002-2024 the BEAST Development Team
 * http://beast.community/about
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 *
 */

package dr.evomodel.siteratemodel;

import dr.evomodel.substmodel.SubstitutionModel;
import dr.inference.model.*;
import dr.math.GeneralisedGaussLaguerreQuadrature;
import dr.math.distributions.BetaDistribution;
import dr.math.distributions.GammaDistribution;
import dr.math.functionEval.GammaFunction;
import dr.util.Author;
import dr.util.Citable;
import dr.util.Citation;

import java.util.ArrayList;
import java.util.List;

/**
 * GammaSiteModel - A SiteModel that has a gamma distributed rates across sites.
 *
 * @author Andrew Rambaut
 */

public class GammaSiteRateDelegate extends AbstractModel implements SiteRateDelegate, Citable {

    /**
     * Constructor for gamma+invar distributed sites. Either shapeParameter or
     * invarParameter (or both) can be null to turn off that feature.
     */
    public GammaSiteRateDelegate(
            String name,
            Parameter shapeParameter,
            int gammaCategoryCount,
            double skew,
            Parameter invarParameter) {

        super(name);

        int catCount;

        this.shapeParameter = shapeParameter;
        if (shapeParameter != null) {
            catCount = gammaCategoryCount;
            addVariable(shapeParameter);
//            shapeParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 1E-3, 1));
            // removing the bounds on the alpha parameter - to make the prior more explicit
            shapeParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0, 1));
        } else {
            catCount = 1;
        }
        this.skew = skew;

        this.invarParameter = invarParameter;
        if (invarParameter != null) {
            catCount += 1;

            addVariable(invarParameter);
            invarParameter.addBounds(new Parameter.DefaultBounds(1.0, 0.0, 1));
        }

        this.categoryCount = catCount;
    }

    // *****************************************************************
    // Interface SiteRateModel
    // *****************************************************************

    public int getCategoryCount() {
        return categoryCount;
    }

    public void getCategories(double[] categoryRates, double[] categoryProportions) {
        assert categoryRates != null && categoryRates.length == categoryCount;
        assert categoryProportions != null && categoryProportions.length == categoryCount;

        int offset = 0;

        if (invarParameter != null) {
            categoryRates[0] = 0.0;
            categoryProportions[0] = invarParameter.getParameterValue(0);
            offset = 1;
        }

        if (shapeParameter != null) {
            double alpha = shapeParameter.getParameterValue(0);
            final int gammaCatCount = categoryCount - offset;

            setRateCategories(categoryRates, categoryProportions, alpha, gammaCatCount, skew, offset);
        } else if (offset > 0) {
            // just the invariant rate and variant rate
            categoryRates[offset] = 2.0;
            categoryProportions[offset] = 1.0 - categoryProportions[0];
        } else {
            categoryRates[0] = 1.0;
            categoryProportions[0] = 1.0;
        }
    }

    // *****************************************************************
    // Interface ModelComponent
    // *****************************************************************

    protected void handleModelChangedEvent(Model model, Object object, int index) {
        listenerHelper.fireModelChanged(this, object, index);
    }

    protected final void handleVariableChangedEvent(Variable variable, int index, Parameter.ChangeType type) {
        listenerHelper.fireModelChanged(this, variable, index);
    }

    protected void storeState() {
    } // no additional state needs storing

    protected void restoreState() {
    }

    protected void acceptState() {
    } // no additional state needs accepting


    /**
     * shape parameter
     */
    private final Parameter shapeParameter;

    /**
     * invariant sites parameter
     */
    private final Parameter invarParameter;


    private final int categoryCount;

    private final double skew;

    @Override
    public Citation.Category getCategory() {
        return Citation.Category.SUBSTITUTION_MODELS;
    }

    @Override
    public String getDescription() {
        return "Discrete gamma-distributed rate heterogeneity model";
    }

    public List<Citation> getCitations() {
        List<Citation> citations = new ArrayList<>();
        if (shapeParameter != null) {
            citations.add(CITATION_YANG94);
        }
        return citations;
    }

    public final static Citation CITATION_YANG94 = new Citation(
            new Author[]{
                    new Author("Z", "Yang")
            },
            "Maximum likelihood phylogenetic estimation from DNA sequences with variable rates over sites: approximate methods",
            1994,
            "J. Mol. Evol.",
            39,
            306, 314,
            Citation.Status.PUBLISHED
    );

    private SubstitutionModel substitutionModel;


    /**
     * set the rates as equally spaced quantiles represented by the mean as proposed by Yang 1994
     *
     * @param categoryRates
     * @param categoryProportions
     * @param alpha
     * @param catCount
     * @param offset
     */
    public static void setRateCategories(double[] categoryRates, double[] categoryProportions, double alpha, int catCount, double skew, int offset) {
        if (skew > 0) {
            double nonInvProp = (offset == 0 ? 1.0 : categoryRates[0]);
            BetaDistribution betaDistribution = new BetaDistribution(1.0, skew + 1);
            for (int i = 0; i < catCount; i++) {
                categoryRates[i + offset] = GammaDistribution.quantile((2.0 * i + 1.0) / (2.0 * catCount), alpha, 1.0 / alpha);
                categoryProportions[i + offset] = nonInvProp * betaDistribution.quantile((2.0 * i + 1.0) / (2.0 * catCount));
            }

        } else {
            for (int i = 0; i < catCount; i++) {
                categoryRates[i + offset] = GammaDistribution.quantile((2.0 * i + 1.0) / (2.0 * catCount), alpha, 1.0 / alpha);
                categoryProportions[i + offset] = 1.0;
            }
        }

        double mean = 0.0;
        for (double categoryRate : categoryRates) {
            mean += categoryRate;
        }
        mean /= categoryRates.length;

        for (int i = 0; i < categoryRates.length; i++) {
            categoryRates[i] /= mean;
        }
    }

    public static void main(String[] argv) {
        final int catCount = 6;

        double[] categoryRates = new double[catCount];
        double[] categoryProportions = new double[catCount];

        setRateCategories(categoryRates, categoryProportions, 1.0, catCount, 0, 0);
        double sumRates = 0.0;
        double sumProps = 0.0;
        System.out.println();
        System.out.println("Equal, alpha = 1.0");
        System.out.println("cat\trate\tproportion");
        for (int i = 0; i < catCount; i++) {
            System.out.println(i + "\t"+ categoryRates[i] +"\t" + categoryProportions[i]);
            sumRates += categoryRates[i];
            sumProps += categoryProportions[i];
        }
        System.out.println("SUM\t"+ sumRates +"\t" + sumProps);

        setRateCategories(categoryRates, categoryProportions, 1.0, catCount, 1, 0);
         sumRates = 0.0;
         sumProps = 0.0;
        System.out.println();
        System.out.println("Skew = 1, alpha = 1.0");
        System.out.println("cat\trate\tproportion");
        for (int i = 0; i < catCount; i++) {
            System.out.println(i + "\t"+ categoryRates[i] +"\t" + categoryProportions[i]);
            sumRates += categoryRates[i];
            sumProps += categoryProportions[i];
        }
        System.out.println("SUM\t"+ sumRates +"\t" + sumProps);

        setRateCategories(categoryRates, categoryProportions, 1.0, catCount, 10, 0);
        sumRates = 0.0;
        sumProps = 0.0;
        System.out.println();
        System.out.println("Skew = 10, alpha = 1.0");
        System.out.println("cat\trate\tproportion");
        for (int i = 0; i < catCount; i++) {
            System.out.println(i + "\t"+ categoryRates[i] +"\t" + categoryProportions[i]);
            sumRates += categoryRates[i];
            sumProps += categoryProportions[i];
        }
        System.out.println("SUM\t"+ sumRates +"\t" + sumProps);

        setRateCategories(categoryRates, categoryProportions, 1.0, catCount, 20, 0);
        sumRates = 0.0;
        sumProps = 0.0;
        System.out.println();
        System.out.println("Skew = 20, alpha = 1.0");
        System.out.println("cat\trate\tproportion");
        for (int i = 0; i < catCount; i++) {
            System.out.println(i + "\t"+ categoryRates[i] +"\t" + categoryProportions[i]);
            sumRates += categoryRates[i];
            sumProps += categoryProportions[i];
        }
        System.out.println("SUM\t"+ sumRates +"\t" + sumProps);

        setRateCategories(categoryRates, categoryProportions, 1.0, catCount, 100, 0);
        sumRates = 0.0;
        sumProps = 0.0;
        System.out.println();
        System.out.println("Skew = 100, alpha = 1.0");
        System.out.println("cat\trate\tproportion");
        for (int i = 0; i < catCount; i++) {
            System.out.println(i + "\t"+ categoryRates[i] +"\t" + categoryProportions[i]);
            sumRates += categoryRates[i];
            sumProps += categoryProportions[i];
        }
        System.out.println("SUM\t"+ sumRates +"\t" + sumProps);

    }
}