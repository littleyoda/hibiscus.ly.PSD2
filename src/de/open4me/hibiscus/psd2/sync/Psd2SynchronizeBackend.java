package de.open4me.hibiscus.psd2.sync;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import de.willuhn.annotation.Lifecycle;
import de.willuhn.annotation.Lifecycle.Type;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.synchronize.AbstractSynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeBackend;
import de.willuhn.jameica.hbci.synchronize.SynchronizeEngine;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;

@Lifecycle(Type.CONTEXT)
public class Psd2SynchronizeBackend extends AbstractSynchronizeBackend<Psd2JobProvider>
{
    public static final String META_CONNECTION_ID = "PSD2-Verbindung";
    public static final String META_IDENTIFICATION_HASH = "PSD2-Identifikationshash";
    public static final String META_ACCOUNT_UID = "PSD2-Konto-UID";
    public static final String META_LAST_TRANSACTION_SYNC = "PSD2-Letzter-Umsatzabruf";
    public static final String META_TRANSACTION_IMPORT_VERSION = "PSD2-Umsatzimport-Version";
    public static final String META_RATE_LIMIT_UNTIL = "PSD2-Abrufsperre-bis";
    public static final String META_HISTORY_RESET_HANDLED = "PSD2-Saldodatum-Reset-verarbeitet";

    @Resource
    private SynchronizeEngine engine;

    @Override
    public String getName()
    {
        return "PSD2 via Enable Banking";
    }

    @Override
    protected Class<Psd2JobProvider> getJobProviderInterface()
    {
        return Psd2JobProvider.class;
    }

    @Override
    protected JobGroup createJobGroup(Konto konto)
    {
        return new Psd2JobGroup(konto);
    }

    @Override
    public List<Konto> getSynchronizeKonten(Konto requested)
    {
        List<Konto> result = new ArrayList<>();
        for (Konto account : super.getSynchronizeKonten(requested))
        {
            SynchronizeBackend backend = engine.getBackend(account);
            if (backend == this)
                result.add(account);
        }
        return result;
    }

    @Override
    public List<String> getPropertyNames(Konto konto)
    {
        return null;
    }

    private final class Psd2JobGroup extends JobGroup
    {
        private Psd2JobGroup(Konto konto)
        {
            super(konto);
        }

        @Override
        protected void sync() throws Exception
        {
            checkInterrupted();
            for (SynchronizeJob job : jobs)
            {
                checkInterrupted();
                ((Psd2SynchronizeJob) job).execute(worker.getMonitor(), () -> worker.isInterrupted());
            }
        }
    }
}
