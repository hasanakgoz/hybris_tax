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
package de.hybris.platform.taxes.controller;

import de.hybris.platform.core.model.order.AbstractOrderModel;
import de.hybris.platform.core.model.user.UserGroupModel;
import de.hybris.platform.taxes.service.TaxcalculationgenericService;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;
import com.hybris.tax.custom.sabrix.DoterraTaxService;


@Controller
public class TaxcalculationgenericHelloController
{
	@Autowired
	private TaxcalculationgenericService taxcalculationgenericService;

	@Autowired
	private DoterraTaxService doterrataxService;

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String printWelcome1(final ModelMap model)
	{
		//model.addAttribute("logoUrl", taxcalculationgenericService.getHybrisLogoUrl(PLATFORM_LOGO_CODE));
		return "welcome";
	}

	@RequestMapping(value = "/logo", method = RequestMethod.GET)
	public String printLogo(final ModelMap model)
	{
		//model.addAttribute("logoUrl", taxcalculationgenericService.getHybrisLogoUrl(PLATFORM_LOGO_CODE));
		return "welcome logo";
	}

	@RequestMapping(value = "/facad", method = RequestMethod.GET)
	public OutdataInvoiceType printWelcome(final AbstractOrderModel cart, final Map<Integer, Map<String, String>> priceDetails,
			final UserGroupModel userGroup)
	{
		try
		{
			return doterrataxService.taxCalculation(cart, priceDetails, userGroup);
		}
		catch (final Exception e)
		{
			// YTODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
