/*******************************************************************************
* Copyright (c) 2021 Red Hat Inc. and others.
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License v. 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
* which is available at https://www.apache.org/licenses/LICENSE-2.0.
*
* SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package com.langserver.devtools.intellij.lsp4mp4ij.psi.core.java.definition;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.langserver.devtools.intellij.lsp4mp4ij.psi.core.java.PropertyReplacerStrategy;
import com.langserver.devtools.intellij.lsp4mp4ij.psi.core.utils.AnnotationMemberInfo;
import com.langserver.devtools.intellij.lsp4mp4ij.psi.core.utils.AnnotationUtils;
import com.langserver.devtools.intellij.lsp4mp4ij.psi.core.utils.IPsiUtils;
import com.langserver.devtools.intellij.lsp4mp4ij.psi.core.utils.PsiTypeUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.util.Ranges;
import org.eclipse.lsp4mp.commons.MicroProfileDefinition;

import java.util.List;
import java.util.function.Function;


/**
 *
 * Abstract class for collecting Java definition participant from a given Java
 * annotation member.
 *
 * @author Angelo ZERR
 *
 */
public abstract class AbstractAnnotationDefinitionParticipant implements IJavaDefinitionParticipant {

	private final String annotationName;

	private final String[] annotationMemberNames;

	private final Function<String, String> propertyReplacer;

	/**
	 * The definition participant constructor.
	 * 
	 * @param annotationName       the annotation name (ex :
	 *                             org.eclipse.microprofile.config.inject.ConfigProperty)
	 * @param annotationMemberNames the annotation member name (ex : name)
	 */
	public AbstractAnnotationDefinitionParticipant(String annotationName, String[] annotationMemberNames) {
		this(annotationName, annotationMemberNames, PropertyReplacerStrategy.NULL_REPLACER);
	}

	/**
	 * The definition participant constructor with a property replacer.
	 *
	 * @param annotationName       the annotation name (ex :
	 *                             io.quarkus.scheduler.Scheduled)
	 * @param annotationMemberNames the supported annotation member names (ex : cron)
	 * @param propertyReplacer     the replacer function for property expressions
	 */
	public AbstractAnnotationDefinitionParticipant(String annotationName, String[] annotationMemberNames,
												   Function<String, String> propertyReplacer) {
		this.annotationName = annotationName;
		this.annotationMemberNames = annotationMemberNames;
		this.propertyReplacer = propertyReplacer;
	}

	@Override
	public boolean isAdaptedForDefinition(JavaDefinitionContext context) {
		// Definition is done only if the annotation is on the classpath
		Module javaProject = context.getJavaProject();
		return PsiTypeUtils.findType(javaProject, annotationName) != null;
	}

	@Override
	public List<MicroProfileDefinition> collectDefinitions(JavaDefinitionContext context) {
		PsiFile typeRoot = context.getTypeRoot();
		IPsiUtils utils = context.getUtils();
		Module javaProject = context.getJavaProject();
		if (javaProject == null) {
			return null;
		}

		// Get the hyperlinked element.
		// If user hyperlinks an annotation, member annotation which is bound a Java
		// field, the hyperlinked Java element is the Java field (not the member or the
		// annotation).
		PsiElement hyperlinkedElement = context.getHyperlinkedElement();
		if (!isAdaptableFor(hyperlinkedElement)) {
			return null;
		}

		Position definitionPosition = context.getHyperlinkedPosition();

		// Try to get the annotation
		PsiAnnotation annotation = AnnotationUtils.getAnnotation(hyperlinkedElement, annotationName);

		if (annotation == null) {
			return null;
		}

		// Try to get the annotation member value
		AnnotationMemberInfo annotationMemberInfo = AnnotationUtils.getAnnotationMemberAt(annotation, annotationMemberNames,
				definitionPosition, typeRoot, utils);
		if (annotationMemberInfo == null) {
			return null;
		}

		String annotationMemberValue = annotationMemberInfo.getMemberValue();
		if (propertyReplacer != null) {
			annotationMemberValue = propertyReplacer.apply(annotationMemberValue);
		}

		// Get the annotation member value range
		final Range annotationMemberValueRange = annotationMemberInfo.getRange();

		if (definitionPosition.equals(annotationMemberValueRange.getEnd())
				|| !Ranges.containsPosition(annotationMemberValueRange, definitionPosition)) {
			return null;
		}

		// Collect definitions
		return collectDefinitions(annotationMemberValue, annotationMemberValueRange, annotation, context);
	}

	/**
	 * Returns true if the given hyperlinked Java element is adapted for this
	 * participant and false otherwise.
	 * 
	 * <p>
	 * 
	 * By default this method returns true if the hyperlinked annotation belongs to
	 * a Java field or local variable and false otherwise.
	 * 
	 * </p>
	 * 
	 * @param hyperlinkedElement the hyperlinked Java element.
	 * 
	 * @return true if the given hyperlinked Java element is adapted for this
	 *         participant and false otherwise.
	 */
	protected boolean isAdaptableFor(PsiElement hyperlinkedElement) {
		return hyperlinkedElement instanceof PsiField
				|| hyperlinkedElement instanceof PsiLocalVariable
				|| hyperlinkedElement instanceof PsiMethod;
	}

	/**
	 * Returns the definitions for the given annotation member value and null
	 * otherwise.
	 * 
	 * @param annotationMemberValue      the annotation member value content.
	 * @param annotationMemberValueRange the annotation member value range.
	 * @param annotation                 the hyperlinked annotation.
	 * @param context                    the definition context.
	 * @return the definitions for the given annotation member value and null
	 *         otherwise.
	 */
	protected abstract List<MicroProfileDefinition> collectDefinitions(String annotationMemberValue,
			Range annotationMemberValueRange, PsiAnnotation annotation, JavaDefinitionContext context);
}
