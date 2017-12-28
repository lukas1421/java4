package auxiliary;

import javax.swing.table.AbstractTableModel;
import java.util.Set;

public class OptParamsModel extends AbstractTableModel {

	private String[] m_expirations;
	private Double[] m_strikes;
	
	public OptParamsModel(Set<String> expirations, Set<Double> strikes) {
		expirations.toArray(m_expirations = new String[expirations.size()]);
		strikes.toArray(m_strikes = new Double[strikes.size()]);		
	}	
	
	@Override
	public int getRowCount() {
		return Math.max(m_expirations.length, m_strikes.length);
	}

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public String getColumnName(int column) {
		switch (column) {
		case 0:
			return "Expirations";
			
		case 1:
			return "Strikes";
		}
		
		return super.getColumnName(column);
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		switch (columnIndex) {
		case 0:
			return rowIndex < m_expirations.length ? m_expirations[rowIndex] : null;

		case 1:
			return rowIndex < m_strikes.length ? m_strikes[rowIndex] : null;

		default:
			return null;
		}
	}

}
