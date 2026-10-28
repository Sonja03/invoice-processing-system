package net.etfbl.mdp.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

import net.etfbl.mdp.common.model.Invoice;
import net.etfbl.mdp.common.model.ValidationResult;

/**
 * RMI interfejs Validator servisa (Faza 3).
 *
 * Potpis metode je propisan zadatkom.
 *
 * Ovo je jedina veza izmedju Parsera i Validatora. Validator ga
 * implementira, Parser ga poziva - a klasu ValidatorServiceImpl
 * Parser nikad ne vidi.
 */
public interface ValidatorService extends Remote {

    ValidationResult validate(Invoice invoice) throws RemoteException;
}