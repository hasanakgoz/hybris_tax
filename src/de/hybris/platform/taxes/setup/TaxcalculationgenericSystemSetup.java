/*
 * [y] hybris Platform
 * 
 * Copyright (c) 2000-2016 SAP SE
 * All rights reserved.
 * 
 * This software is the confidential and proprietary information of SAP 
 * Hybris ("Confidential Information"). You shall not disclose such 
 * Confidential Information and shall use it only in accordance with the 
 * terms of the license agreement you entered into with SAP Hybris.
 */
package de.hybris.platform.taxes.setup;

import static de.hybris.platform.taxes.constants.TaxcalculationgenericConstants.PLATFORM_LOGO_CODE;

import de.hybris.platform.core.initialization.SystemSetup;

import java.io.InputStream;

import de.hybris.platform.taxes.constants.TaxcalculationgenericConstants;
import de.hybris.platform.taxes.service.TaxcalculationgenericService;


@SystemSetup(extension = TaxcalculationgenericConstants.EXTENSIONNAME)
public class TaxcalculationgenericSystemSetup
{
	private final TaxcalculationgenericService taxcalculationgenericService;

	public TaxcalculationgenericSystemSetup(final TaxcalculationgenericService taxcalculationgenericService)
	{
		this.taxcalculationgenericService = taxcalculationgenericService;
	}

	@SystemSetup(process = SystemSetup.Process.INIT, type = SystemSetup.Type.ESSENTIAL)
	public void createEssentialData()
	{
		taxcalculationgenericService.createLogo(PLATFORM_LOGO_CODE);
	}

	private InputStream getImageStream()
	{
		return TaxcalculationgenericSystemSetup.class.getResourceAsStream("/taxcalculationgeneric/sap-hybris-platform.png");
	}
}
