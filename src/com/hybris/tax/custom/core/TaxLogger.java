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
package com.hybris.tax.custom.core;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import org.apache.log4j.Logger;


/*
 * This simple SOAPHandler will output the contents of incoming
 * and outgoing messages.
 */
public class TaxLogger implements SOAPHandler<SOAPMessageContext>
{

	private static final Logger LOG = Logger.getLogger(TaxLogger.class);


	// change this to redirect output if desired
	public Set<QName> getHeaders()
	{
		return null;
	}

	public boolean handleMessage(final SOAPMessageContext smc)
	{
		logToSystemOut(smc);
		return true;
	}

	public boolean handleFault(final SOAPMessageContext smc)
	{
		logToSystemOut(smc);
		return true;
	}

	//	//Nothing to clean here
	//	public void close(final MessageContext messageContext)
	//	{
	//
	//	}

	/*
	 * Check the MESSAGE_OUTBOUND_PROPERTY in the context to see if this is an outgoing or incoming message. Write a
	 * brief message to the print stream and output the message. The writeTo() method can throw SOAPException or
	 * IOException
	 */

	//TODO: change these to log.debug?? With lots of orders, this is filling up the log files.
	private void logToSystemOut(final SOAPMessageContext smc)
	{
		if (LOG.isDebugEnabled())
		{
			final Boolean outboundProperty = (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);

			if (outboundProperty.booleanValue())
			{
				LOG.debug("\nOutbound message:");
			}
			else
			{
				LOG.debug("\nInbound message:");
			}

			final SOAPMessage message = smc.getMessage();
			try
			{
				final OutputStream os = new ByteArrayOutputStream();
				message.writeTo(os);
				LOG.debug(os.toString());
			}
			catch (final Exception e)
			{
				LOG.error("Exception in handler: " + e);
			}
		}
	}

	@Override
	public void close(final MessageContext context)
	{
		// YTODO Auto-generated method stub

	}
}

