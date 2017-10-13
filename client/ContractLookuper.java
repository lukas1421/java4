package client;

import java.util.ArrayList;

@FunctionalInterface
public interface ContractLookuper {
	ArrayList<ContractDetails> lookupContract(Contract contract);
}