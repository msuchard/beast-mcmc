/*
 * MultiSpeciesCoalescentGenerator.java
 *
 * Copyright (C) 2002-2009 Alexei Drummond and Andrew Rambaut
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
 * BEAST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.app.beauti.generator;

import dr.app.beauti.components.ComponentFactory;
import dr.app.beauti.enumTypes.PopulationSizeModelType;
import dr.app.beauti.enumTypes.TreePriorType;
import dr.app.beauti.options.BeautiOptions;
import dr.app.beauti.options.Parameter;
import dr.app.beauti.options.PartitionTreeModel;
import dr.app.beauti.options.TraitGuesser;
import dr.app.beauti.util.XMLWriter;
import dr.evolution.datatype.PloidyType;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.evomodel.speciation.MultiSpeciesCoalescent;
import dr.evomodel.speciation.SpeciationLikelihood;
import dr.evomodel.speciation.SpeciesBindings;
import dr.evomodel.speciation.SpeciesTreeModel;
import dr.evomodel.tree.TMRCAStatistic;
import dr.evomodel.tree.TreeModel;
import dr.evomodelxml.TreeModelParser;
import dr.evomodelxml.speciation.BirthDeathModelParser;
import dr.evomodelxml.speciation.YuleModelParser;
import dr.evoxml.TaxaParser;
import dr.evoxml.TaxonParser;
import dr.evoxml.util.XMLUnits;
import dr.inference.distribution.GammaDistributionModel;
import dr.inference.distribution.MixedDistributionLikelihood;
import dr.inference.model.ParameterParser;
import dr.inferencexml.DistributionModelParser;
import dr.util.Attribute;
import dr.xml.AttributeParser;
import dr.xml.XMLParser;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Drummond
 * @author Walter Xie
 */
public class STARBEASTGenerator extends Generator {

    private int numOfSpecies; // used in private String getIndicatorsParaValue()

    public STARBEASTGenerator(BeautiOptions options, ComponentFactory[] components) {
        super(options, components);
    }

    /**
     * write tag <sp>
     *
     * @param taxonList  TaxonList
     * @param writer    XMLWriter
     */
    public void writeMultiSpecies(TaxonList taxonList, XMLWriter writer) {
        List<String> species = options.starBEASTOptions.getSpeciesList();
        String sp;

        numOfSpecies = species.size(); // used in private String getIndicatorsParaValue()

        for (String eachSp : species) {
            writer.writeOpenTag(SpeciesBindings.SP, new Attribute[]{new Attribute.Default<String>(XMLParser.ID, eachSp)});

            for (int i = 0; i < taxonList.getTaxonCount(); i++) {
                Taxon taxon = taxonList.getTaxon(i);
                sp = taxon.getAttribute(TraitGuesser.Traits.TRAIT_SPECIES.toString()).toString();

                if (sp.equals(eachSp)) {
                    writer.writeIDref(TaxonParser.TAXON, taxon.getId());
                }

            }
            writer.writeCloseTag(SpeciesBindings.SP);
        }

        writeGeneTrees(writer);
    }

    /**
     * write the species tree, species tree model, likelihood, etc.
     *
     * @param writer  XMLWriter
     */
    public void writeSTARBEAST(XMLWriter writer) {
        writeSpeciesTree(writer);
        writeSpeciesTreeModel(writer);
        writeSpeciesTreeLikelihood(writer);
        writeSpeciesTreeRootHeight(writer);
        writeGeneUnderSpecies(writer);
    }


    private void writeGeneTrees(XMLWriter writer) {
        writer.writeComment("Collection of Gene Trees");

        writer.writeOpenTag(SpeciesBindings.GENE_TREES, new Attribute[]{new Attribute.Default<String>(XMLParser.ID, SpeciesBindings.GENE_TREES)});
        
        boolean isSameAllPloidyType = true;
        PloidyType checkSamePloidyType = options.getPartitionTreeModels().get(0).getPloidyType();
        for (PartitionTreeModel model : options.getPartitionTreeModels()) {
        	if (checkSamePloidyType != model.getPloidyType()) {
        		isSameAllPloidyType = false;
        		break;
        	}
        }
        
        if (isSameAllPloidyType) {
	        // generate gene trees regarding each data partition
	        for (PartitionTreeModel model : options.getPartitionTreeModels()) {
	            writer.writeIDref(TreeModel.TREE_MODEL, model.getPrefix() + TreeModel.TREE_MODEL);
	        }
        } else {
        	// give ploidy
	        for (PartitionTreeModel model : options.getPartitionTreeModels()) {
	            writer.writeOpenTag(SpeciesBindings.GTREE, new Attribute[]{
		                new Attribute.Default<String>(SpeciesBindings.PLOIDY, Double.toString(model.getPloidyType().getValue()))
		            }
		        );
	            writer.writeIDref(TreeModel.TREE_MODEL, model.getPrefix() + TreeModel.TREE_MODEL);
	            writer.writeCloseTag(SpeciesBindings.GTREE);
	        }
        }
        
        writer.writeCloseTag(SpeciesBindings.GENE_TREES);
    }


    private void writeSpeciesTree(XMLWriter writer) {
        writer.writeComment("Species Tree: Provides Per branch demographic function");

        List<Attribute> attributes = new ArrayList<Attribute>();

        attributes.add(new Attribute.Default<String>(XMLParser.ID, SP_TREE));
        // *BEAST always share same tree prior
        if (options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONTINUOUS_CONSTANT) {
              attributes.add(new Attribute.Default<String>(SpeciesTreeModel.CONST_ROOT_POPULATION, "true"));
        } else if (options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONSTANT) {
              attributes.add(new Attribute.Default<String>(SpeciesTreeModel.CONSTANT_POPULATION, "true"));
        }

        writer.writeOpenTag(SpeciesTreeModel.SPECIES_TREE, attributes);
        
        writer.writeIDref(TraitGuesser.Traits.TRAIT_SPECIES.toString(), TraitGuesser.Traits.TRAIT_SPECIES.toString());
                
        // take sppSplitPopulations value from partionModel(?).constant.popSize
        // *BEAST always share same tree prior
        double popSizeValue = options.getPartitionTreePriors().get(0).getParameter("constant.popSize").initial; // "initial" is "value"
        writer.writeOpenTag(SpeciesTreeModel.SPP_SPLIT_POPULATIONS, new Attribute[]{
                new Attribute.Default<String>(ParameterParser.VALUE, Double.toString(popSizeValue))});

        writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
                new Attribute.Default<String>(XMLParser.ID, SpeciesTreeModel.SPECIES_TREE + "." + SPLIT_POPS)}, true);

        writer.writeCloseTag(SpeciesTreeModel.SPP_SPLIT_POPULATIONS);

        writer.writeCloseTag(SpeciesTreeModel.SPECIES_TREE);

    }

    private void writeSpeciesTreeModel(XMLWriter writer) {
        Parameter para;

        writer.writeComment("Species Tree: tree prior");

        if (options.getPartitionTreePriors().get(0).getNodeHeightPrior() == TreePriorType.SPECIES_BIRTH_DEATH) {
            writer.writeComment("Species Tree: Birth Death Model");

            writer.writeOpenTag(BirthDeathModelParser.BIRTH_DEATH_MODEL, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, BirthDeathModelParser.BIRTH_DEATH),
                    new Attribute.Default<String>(XMLUnits.UNITS, XMLUnits.SUBSTITUTIONS)});

            writer.writeOpenTag(BirthDeathModelParser.BIRTHDIFF_RATE);

            para = options.starBEASTOptions.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.MEAN_GROWTH_RATE_PARAM_NAME);
            writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.MEAN_GROWTH_RATE_PARAM_NAME),
                    new Attribute.Default<String>(ParameterParser.VALUE, Double.toString(para.initial)),
                    new Attribute.Default<String>(ParameterParser.LOWER, Double.toString(para.lower)),
                    new Attribute.Default<String>(ParameterParser.UPPER, Double.toString(para.upper))}, true);

            writer.writeCloseTag(BirthDeathModelParser.BIRTHDIFF_RATE);

            writer.writeOpenTag(BirthDeathModelParser.RELATIVE_DEATH_RATE);

            para = options.starBEASTOptions.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.RELATIVE_DEATH_RATE_PARAM_NAME);
            writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + BirthDeathModelParser.RELATIVE_DEATH_RATE_PARAM_NAME),
                    new Attribute.Default<String>(ParameterParser.VALUE, Double.toString(para.initial)),
                    new Attribute.Default<String>(ParameterParser.LOWER, Double.toString(para.lower)),
                    new Attribute.Default<String>(ParameterParser.UPPER, Double.toString(para.upper))}, true);

            writer.writeCloseTag(BirthDeathModelParser.RELATIVE_DEATH_RATE);

            writer.writeCloseTag(BirthDeathModelParser.BIRTH_DEATH_MODEL);
        } else if (options.getPartitionTreePriors().get(0).getNodeHeightPrior() == TreePriorType.SPECIES_YULE) {
            writer.writeComment("Species Tree: Yule Model");

            writer.writeOpenTag(YuleModelParser.YULE_MODEL, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, YuleModelParser.YULE),
                    new Attribute.Default<String>(XMLUnits.UNITS, XMLUnits.SUBSTITUTIONS)});

            writer.writeOpenTag(YuleModelParser.BIRTH_RATE);

            para = options.starBEASTOptions.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + YuleModelParser.YULE + "." + YuleModelParser.BIRTH_RATE);
            writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + YuleModelParser.YULE + "." + YuleModelParser.BIRTH_RATE),
                    new Attribute.Default<String>(ParameterParser.VALUE, Double.toString(para.initial)),
                    new Attribute.Default<String>(ParameterParser.LOWER, Double.toString(para.lower)),
                    new Attribute.Default<String>(ParameterParser.UPPER, Double.toString(para.upper))}, true);

            writer.writeCloseTag(YuleModelParser.BIRTH_RATE);

            writer.writeCloseTag(YuleModelParser.YULE_MODEL);
        } else {
        	throw new IllegalArgumentException("Get wrong species tree prior using *BEAST : " + options.getPartitionTreePriors().get(0).getNodeHeightPrior().toString());
        }

    }


    private void writeSpeciesTreeLikelihood(XMLWriter writer) {
        writer.writeComment("Species Tree: Likelihood of species tree");

        if (options.getPartitionTreePriors().get(0).getNodeHeightPrior() == TreePriorType.SPECIES_BIRTH_DEATH) {
            writer.writeComment("Species Tree: Birth Death Model");

            writer.writeOpenTag(SpeciationLikelihood.SPECIATION_LIKELIHOOD, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, SPECIATION_LIKE)});

            writer.writeOpenTag(SpeciationLikelihood.MODEL);
            writer.writeIDref(BirthDeathModelParser.BIRTH_DEATH_MODEL, BirthDeathModelParser.BIRTH_DEATH);
            writer.writeCloseTag(SpeciationLikelihood.MODEL);

        } else if (options.getPartitionTreePriors().get(0).getNodeHeightPrior() == TreePriorType.SPECIES_YULE) {
            writer.writeComment("Species Tree: Yule Model");

            writer.writeOpenTag(SpeciationLikelihood.SPECIATION_LIKELIHOOD, new Attribute[]{
                    new Attribute.Default<String>(XMLParser.ID, SPECIATION_LIKE)});

            writer.writeOpenTag(SpeciationLikelihood.MODEL);
            writer.writeIDref(YuleModelParser.YULE_MODEL, YuleModelParser.YULE);
            writer.writeCloseTag(SpeciationLikelihood.MODEL);
        } else {
        	throw new IllegalArgumentException("Get wrong species tree prior using *BEAST : "
                    + options.getPartitionTreePriors().get(0).getNodeHeightPrior().toString());
        }

        // <sp> tree
        writer.writeOpenTag(SpeciesTreeModel.SPECIES_TREE);
        writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE);
        writer.writeCloseTag(SpeciesTreeModel.SPECIES_TREE);

        writer.writeCloseTag(SpeciationLikelihood.SPECIATION_LIKELIHOOD);
    }

    private void writeSpeciesTreeRootHeight(XMLWriter writer) {
    	writer.writeComment("Species Tree: tmrcaStatistic");
    	
    	writer.writeOpenTag(TMRCAStatistic.TMRCA_STATISTIC, new Attribute[]{
                new Attribute.Default<String>(XMLParser.ID, SpeciesTreeModel.SPECIES_TREE + "." + TreeModelParser.ROOT_HEIGHT),
                new Attribute.Default<String>(AttributeParser.NAME, SpeciesTreeModel.SPECIES_TREE + "." + TreeModelParser.ROOT_HEIGHT)});
    	
    	writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE);

        writer.writeOpenTag(TMRCAStatistic.MRCA);
        writer.writeOpenTag(TaxaParser.TAXA);

        for (String eachSp : options.starBEASTOptions.getSpeciesList()) {
        	writer.writeIDref(SpeciesBindings.SP, eachSp);
        }
        
        writer.writeCloseTag(TaxaParser.TAXA);
        writer.writeCloseTag(TMRCAStatistic.MRCA);
        writer.writeCloseTag(TMRCAStatistic.TMRCA_STATISTIC);
    	
    }
    
    private void writeGeneUnderSpecies(XMLWriter writer) {

        writer.writeComment("Species Tree: Coalescent likelihood for gene trees under species tree");

        // speciesCoalescent id="coalescent"
        writer.writeOpenTag(MultiSpeciesCoalescent.SPECIES_COALESCENT, new Attribute[]{
                new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + COALESCENT)});

        writer.writeIDref(TraitGuesser.Traits.TRAIT_SPECIES.toString(), TraitGuesser.Traits.TRAIT_SPECIES.toString());
        writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE);

        writer.writeCloseTag(MultiSpeciesCoalescent.SPECIES_COALESCENT);

        // exponentialDistributionModel id="pdist"
//        writer.writeOpenTag(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL, new Attribute[]{
//                new Attribute.Default<String>(XMLParser.ID, PDIST)});
//
//        writer.writeOpenTag(DistributionModelParser.MEAN);
//
//        Parameter para = options.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + options.POP_MEAN);
//
//        writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
//                new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + options.POP_MEAN),
//                new Attribute.Default<String>(ParameterParser.VALUE, Double.toString(para.initial))}, true);
//
//        writer.writeCloseTag(DistributionModelParser.MEAN);
//
//        writer.writeCloseTag(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL);

//        if (options.speciesTreePrior == TreePriorType.SPECIES_YULE) {
        
        writer.writeComment("Species tree prior: gama2 + gamma4");
        writer.writeOpenTag(MixedDistributionLikelihood.DISTRIBUTION_LIKELIHOOD, new Attribute[]{
                new Attribute.Default<String>(XMLParser.ID, SPOPS)});
        
        // change exponential + gamma2 into gama2 + gamma4
        // <distribution0>
        writer.writeOpenTag(MixedDistributionLikelihood.DISTRIBUTION0);
//        writer.writeIDref(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL, PDIST); 
        writer.writeOpenTag(GammaDistributionModel.GAMMA_DISTRIBUTION_MODEL);
        writer.writeOpenTag(DistributionModelParser.SHAPE);
        writer.writeText("2");
        writer.writeCloseTag(DistributionModelParser.SHAPE);

        writer.writeOpenTag(DistributionModelParser.SCALE);
        
        Parameter para = options.starBEASTOptions.getParameter(TraitGuesser.Traits.TRAIT_SPECIES + "." + options.starBEASTOptions.POP_MEAN);        
        writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
                new Attribute.Default<String>(XMLParser.ID, TraitGuesser.Traits.TRAIT_SPECIES + "." + options.starBEASTOptions.POP_MEAN),
                new Attribute.Default<String>(ParameterParser.VALUE, Double.toString(para.initial))}, true);
        
        writer.writeCloseTag(DistributionModelParser.SCALE);

        writer.writeCloseTag(GammaDistributionModel.GAMMA_DISTRIBUTION_MODEL);   
        writer.writeCloseTag(MixedDistributionLikelihood.DISTRIBUTION0);

        // <distribution1>
        writer.writeOpenTag(MixedDistributionLikelihood.DISTRIBUTION1);
        writer.writeOpenTag(GammaDistributionModel.GAMMA_DISTRIBUTION_MODEL);

        writer.writeOpenTag(DistributionModelParser.SHAPE);
        writer.writeText("4");
        writer.writeCloseTag(DistributionModelParser.SHAPE);

        writer.writeOpenTag(DistributionModelParser.SCALE);
        writer.writeIDref(ParameterParser.PARAMETER, TraitGuesser.Traits.TRAIT_SPECIES + "." + options.starBEASTOptions.POP_MEAN);
        writer.writeCloseTag(DistributionModelParser.SCALE);

        writer.writeCloseTag(GammaDistributionModel.GAMMA_DISTRIBUTION_MODEL);
        writer.writeCloseTag(MixedDistributionLikelihood.DISTRIBUTION1);

        // <data>
        writer.writeOpenTag(MixedDistributionLikelihood.DATA);

        writer.writeIDref(ParameterParser.PARAMETER, SpeciesTreeModel.SPECIES_TREE + "." + SPLIT_POPS);

        writer.writeCloseTag(MixedDistributionLikelihood.DATA);

        // <indicators>
        writer.writeOpenTag(MixedDistributionLikelihood.INDICATORS);
        // Needs special treatment - you have to generate "NS" ones and 2(N-1) zeros, where N is the number of species.
        // N "1", 2(N-1) "0"
        writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{new Attribute.Default<String>(ParameterParser.VALUE, getIndicatorsParaValue())}, true);

        writer.writeCloseTag(MixedDistributionLikelihood.INDICATORS);

        writer.writeCloseTag(MixedDistributionLikelihood.DISTRIBUTION_LIKELIHOOD);

//        } else {
//            // STPopulationPrior id="stp" log_root="true"
//            writer.writeOpenTag(SpeciesTreeBMPrior.STPRIOR, new Attribute[]{
//                    new Attribute.Default<String>(XMLParser.ID, STP),
//                    new Attribute.Default<String>(SpeciesTreeBMPrior.LOG_ROOT, "true")});
//            writer.writeIDref(SpeciesTreeModel.SPECIES_TREE, SP_TREE);
//
//            writer.writeOpenTag(SpeciesTreeBMPrior.TIPS);
//
//            writer.writeIDref(ExponentialDistributionModel.EXPONENTIAL_DISTRIBUTION_MODEL, PDIST);
//
//            writer.writeCloseTag(SpeciesTreeBMPrior.TIPS);
//
//            writer.writeOpenTag(SpeciesTreeBMPrior.STSIGMA);
//
//            writer.writeTag(ParameterParser.PARAMETER, new Attribute[]{
//                    // <parameter id="stsigma" value="1" />
//                    new Attribute.Default<String>(XMLParser.ID, SpeciesTreeBMPrior.STSIGMA.toLowerCase()),
//                    new Attribute.Default<String>(ParameterParser.VALUE, "1")}, true);
//
//            writer.writeCloseTag(SpeciesTreeBMPrior.STSIGMA);
//
//            writer.writeCloseTag(SpeciesTreeBMPrior.STPRIOR);
//        }
    }

    private String getIndicatorsParaValue() {
        String v = "";

        // CONTINUOUS_CONSTANT    N  1      2(N-1) 0
        // CONTINUOUS             N  1      2N-1   0
        // CONSTANT                         2N-1   0   
        if (options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONTINUOUS_CONSTANT
                || options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONTINUOUS) {
            for (int i = 0; i < numOfSpecies; i++) {
                if (i == (numOfSpecies - 1)) {
                    v = v + "1"; // N 1
                } else {
                    v = v + "1 "; // N 1
                }
            }
        }

        if (options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONTINUOUS_CONSTANT) {
            for (int i = 0; i < (numOfSpecies - 1); i++) {
                v = v + " 0 0"; // 2(N-1) 0
            }
        } else if (options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONTINUOUS) {
            v = v + " 0"; // 1   0
            for (int i = 0; i < (numOfSpecies - 1); i++) {
                v = v + " 0 0"; // 2(N-1) 0
            }
        } else if (options.getPartitionTreePriors().get(0).getPopulationSizeModel() == PopulationSizeModelType.CONSTANT) {
            v = v + "0"; // 1   0
            for (int i = 0; i < (numOfSpecies - 1); i++) {
                v = v + " 0 0"; // 2(N-1) 0
            }
        }
        
        return v;
    }
}
