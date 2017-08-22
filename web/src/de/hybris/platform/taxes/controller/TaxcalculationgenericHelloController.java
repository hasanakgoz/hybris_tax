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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.hybris.doterra.process.tax.sabrix.services.taxcalculationservice.OutdataInvoiceType;
import com.hybris.tax.custom.core.ISabrixCall;


@Controller
public class TaxcalculationgenericHelloController
{
	@Autowired
	private ISabrixCall sabrixCall;

	@RequestMapping(value = "/sabrixcall", method = RequestMethod.GET)
	public OutdataInvoiceType printWelcome(final AbstractOrderModel cart)
	{
		return sabrixCall.taxCalculation(cart);

	}
}
