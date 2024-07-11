/*
 * SiteModelParser.java
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

package dr.evomodelxml.siteratemodel;

import java.util.logging.Logger;

import dr.evomodel.siteratemodel.*;
import dr.evomodel.substmodel.SubstitutionModel;
import dr.oldevomodel.sitemodel.SiteModel;
import dr.inference.model.Parameter;
import dr.xml.*;

/**
 * This is a replacement to GammaSiteModelParser to keep old XML that used
 * the <siteModel></siteModel> element working.
 * @author Andrew Rambaut
 */
public class SiteModelParser extends AbstractXMLObjectParser {

    public static final String SITE_MODEL = "siteModel";
    public static final String SUBSTITUTION_MODEL = "substitutionModel";
    public static final String MUTATION_RATE = "mutationRate";
    public static final String SUBSTITUTION_RATE = "substitutionRate";
    public static final String RELATIVE_RATE = "relativeRate";
    public static final String WEIGHT = "weight";
    public static final String SKEW = "skew";
    public static final String FREE_RATES = "freeRates";
    public static final String RATES = "rates";
    public static final String WEIGHTS = "weights";
    public static final String GAMMA_SHAPE = "gammaShape";
    public static final String RATE_CATEGORIES = "rateCategories";
    public static final String GAMMA_CATEGORIES = "gammaCategories";
    public static final String PROPORTION_INVARIANT = "proportionInvariant";
    public static final String DISCRETIZATION = "discretization";

    public String getParserName() {
        return  SITE_MODEL;
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        String msg = "";
        SubstitutionModel substitutionModel;

        double muWeight = 1.0;

        Parameter muParam = null;
        if (xo.hasChildNamed(SUBSTITUTION_RATE)) {
            muParam = (Parameter) xo.getElementFirstChild(SUBSTITUTION_RATE);

            msg += "\n  with initial substitution rate = " + muParam.getParameterValue(0);
        } else  if (xo.hasChildNamed(MUTATION_RATE)) {
            muParam = (Parameter) xo.getElementFirstChild(MUTATION_RATE);

            msg += "\n  with initial substitution rate = " + muParam.getParameterValue(0);
        } else if (xo.hasChildNamed(RELATIVE_RATE)) {
            XMLObject cxo = xo.getChild(RELATIVE_RATE);
            muParam = (Parameter) cxo.getChild(Parameter.class);
            msg += "\n  with initial relative rate = " + muParam.getParameterValue(0);
            if (cxo.hasAttribute(WEIGHT)) {
                muWeight = cxo.getDoubleAttribute(WEIGHT);
                msg += " with weight: " + muWeight;
            }
        }

        Parameter shapeParameter = null;
        Parameter invarParameter = null;
        Parameter ratesParameter = null;
        Parameter weightsParameter = null;
        int catCount = 4;
        double skew = 0.0;

        if (xo.hasChildNamed(FREE_RATES)) {
            catCount = xo.getIntegerAttribute(RATE_CATEGORIES);

            ratesParameter = (Parameter) xo.getElementFirstChild(RATES);
            weightsParameter = (Parameter) xo.getElementFirstChild(WEIGHTS);

            msg += "\n  " + catCount + " category free rate model";

        } else if (xo.hasChildNamed(GAMMA_SHAPE)) {


            if (xo.hasChildNamed(GAMMA_SHAPE)) {
                XMLObject cxo = xo.getChild(GAMMA_SHAPE);
                catCount = cxo.getIntegerAttribute(GAMMA_CATEGORIES);

                if ( xo.hasAttribute(SKEW)) {
                    skew = xo.getDoubleAttribute(SKEW);
                }
                if (skew < 0.0) {
                    throw new XMLParseException("Gamma weight skew must be >= 0.0");
                }

                shapeParameter = (Parameter) cxo.getChild(Parameter.class);

                msg += "\n  " + catCount + " category discrete gamma with initial shape = " + shapeParameter.getParameterValue(0);
                if (skew == 0.0) {
                    msg += "\n  using equal weight discretization of gamma distribution";
                } else {
                    msg += "\n  using skewed weight discretization of gamma distribution, skew = " + skew;
                }
            }

            if (xo.hasChildNamed(PROPORTION_INVARIANT)) {
                invarParameter = (Parameter) xo.getElementFirstChild(PROPORTION_INVARIANT);
                msg += "\n  initial proportion of invariant sites = " + invarParameter.getParameterValue(0);
            }
        }

        if (!msg.isEmpty()) {
            Logger.getLogger("dr.evomodel").info("\nCreating site rate model: " + msg);
        } else {
            Logger.getLogger("dr.evomodel").info("\nCreating site rate model.");
        }

        SiteRateDelegate delegate;
        if (ratesParameter != null && weightsParameter != null) {
            delegate = new FreeRateDelegate("FreeRateDelegate", catCount, ratesParameter, weightsParameter, invarParameter);
        } else if (shapeParameter != null || invarParameter != null) {
            delegate = new GammaSiteRateDelegate("GammaSiteRateDelegate", shapeParameter, catCount, skew, invarParameter);
        } else {
            delegate = new HomogeneousRateDelegate("HomogeneousRateDelegate");
        }


        DiscretizedSiteRateModel siteRateModel = new DiscretizedSiteRateModel(SiteModel.SITE_MODEL, muParam, muWeight, delegate);

        if (xo.hasChildNamed(SUBSTITUTION_MODEL)) {

//        	System.err.println("Doing the substitution model stuff");

            // set this to pass it along to the OldTreeLikelihoodParser...
            substitutionModel = (SubstitutionModel) xo.getElementFirstChild(SUBSTITUTION_MODEL);
            siteRateModel.setSubstitutionModel(substitutionModel);

        }

        return siteRateModel;
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public String getParserDescription() {
        return "A DiscretizedSiteRateModel that has a gamma distributed rates across sites";
    }

    @Override
    public String[] getParserNames() {
        return super.getParserNames();
    }

    public Class<DiscretizedSiteRateModel> getReturnType() {
        return DiscretizedSiteRateModel.class;
    }

    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }

    private final XMLSyntaxRule[] rules = {

            new ElementRule(SUBSTITUTION_MODEL, new XMLSyntaxRule[]{
                    new ElementRule(SubstitutionModel.class)
            }, true),

            new XORRule(
                    new XORRule(
                            new ElementRule(SUBSTITUTION_RATE, new XMLSyntaxRule[]{
                                    new ElementRule(Parameter.class)
                            }),
                            new ElementRule(MUTATION_RATE, new XMLSyntaxRule[]{
                                    new ElementRule(Parameter.class)
                            })
                    ),
                    new ElementRule(RELATIVE_RATE, new XMLSyntaxRule[]{
                            AttributeRule.newDoubleRule(WEIGHT, true),
                            new ElementRule(Parameter.class)
                    }), true
            ),

            new XORRule(
                    new AndRule(new XMLSyntaxRule[]{
                            new ElementRule(GAMMA_SHAPE, new XMLSyntaxRule[]{
                                    AttributeRule.newIntegerRule(GAMMA_CATEGORIES, true),
                                    AttributeRule.newIntegerRule(RATE_CATEGORIES, true),
                                    AttributeRule.newStringRule(DISCRETIZATION, true),
                                    new ElementRule(Parameter.class)
                            }, true),
                            new ElementRule(PROPORTION_INVARIANT, new XMLSyntaxRule[]{
                                    new ElementRule(Parameter.class)
                            }, true)
                    }),
                    new ElementRule(FREE_RATES, new XMLSyntaxRule[]{
                            AttributeRule.newIntegerRule(RATE_CATEGORIES, true),
                            AttributeRule.newStringRule(DISCRETIZATION, true),
                            new ElementRule(Parameter.class)
                    }, true)
            )
    };

}//END: class
