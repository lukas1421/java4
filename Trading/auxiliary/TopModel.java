package auxiliary;

import api.ChinaMain;
import client.Contract;
import client.TickType;
import client.Types.MktDataType;
import controller.ApiController.TopMktDataAdapter;
import controller.Formats;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;

import static controller.Formats.*;

public class TopModel extends AbstractTableModel {

    private final ArrayList<TopRow> m_rows = new ArrayList<>();

    @SuppressWarnings("unused")
    void addRow(Contract contract) {
        System.out.println("add row started");
        TopRow row = new TopRow(this, contract.description());
        m_rows.add(row);
        ChinaMain.controller().reqTopMktData(contract, "", false, row);
        fireTableRowsInserted(m_rows.size() - 1, m_rows.size() - 1);
        System.out.println("add row ended");
    }

    @SuppressWarnings("unused")
    void addRow(TopRow row) {
        m_rows.add(row);
        fireTableRowsInserted(m_rows.size() - 1, m_rows.size() - 1);
    }

    @SuppressWarnings("SpellCheckingInspection")
    public void desubscribe() {
        m_rows.forEach((row) -> ChinaMain.controller().cancelTopMktData(row));
    }

    @Override
    public int getRowCount() {
        return m_rows.size();
    }

    @Override
    public int getColumnCount() {
        return 9;
    }

    @Override
    public String getColumnName(int col) {
        switch (col) {
            case 0:
                return "Description";
            case 1:
                return "Bid Size";
            case 2:
                return "Bid";
            case 3:
                return "Ask";
            case 4:
                return "Ask Size";
            case 5:
                return "Last";
            case 6:
                return "Time";
            case 7:
                return "Change";
            case 8:
                return "Volume";
            default:
                return null;
        }
    }

    @Override
    public Object getValueAt(int rowIn, int col) {
        TopRow row = m_rows.get(rowIn);
        switch (col) {
            case 0:
                return row.m_description;
            case 1:
                return row.m_bidSize;
            case 2:
                return fmt(row.m_bid);
            case 3:
                return fmt(row.m_ask);
            case 4:
                return row.m_askSize;
            case 5:
                return fmt(row.m_last);
            case 6:
                return fmtTime(row.m_lastTime);
            case 7:
                return row.change();
            case 8:
                return Formats.fmt0(row.m_volume);
            default:
                return null;
        }
    }

    public void color(TableCellRenderer rend, int rowIn, Color def) {
        TopRow row = m_rows.get(rowIn);
        Color c = row.m_frozen ? Color.gray : def;
        ((JLabel) rend).setForeground(c);
    }

    @SuppressWarnings("unused")
    public void cancel(int i) {
        ChinaMain.controller().cancelTopMktData(m_rows.get(i));
    }

    static class TopRow extends TopMktDataAdapter {

        AbstractTableModel m_model;
        String m_description;
        double m_bid;
        double m_ask;
        double m_last;
        long m_lastTime;
        int m_bidSize;
        int m_askSize;
        double m_close;
        int m_volume;
        boolean m_frozen;

        TopRow(AbstractTableModel model, String description) {
            m_model = model;
            m_description = description;
        }

        String change() {
            return m_close == 0 ? null : fmtPct((m_last - m_close) / m_close);
        }

        @Override
        public void tickPrice(TickType tickType, double price, int canAutoExecute) {
            switch (tickType) {
                case BID:
                    m_bid = price;
                    break;
                case ASK:
                    m_ask = price;
                    break;
                case LAST:
                    m_last = price;
                    break;
                case CLOSE:
                    m_close = price;
                    break;
            }
            m_model.fireTableDataChanged(); // should use a timer to be more efficient
        }

        @Override
        public void tickSize(TickType tickType, int size) {
            switch (tickType) {
                case BID_SIZE:
                    m_bidSize = size;
                    break;
                case ASK_SIZE:
                    m_askSize = size;
                    break;
                case VOLUME:
                    m_volume = size;
                    break;
            }
            m_model.fireTableDataChanged();
        }

        @Override
        public void tickString(TickType tickType, String value) {
            switch (tickType) {
                case LAST_TIMESTAMP:
                    m_lastTime = Long.parseLong(value) * 1000;
                    break;
            }
        }

        @Override
        public void marketDataType(MktDataType marketDataType) {
            m_frozen = marketDataType == MktDataType.Frozen;
            m_model.fireTableDataChanged();
        }
    }
}
