package de.open4me.hibiscus.psd2.sync;

import java.util.List;

import javax.annotation.Resource;

import de.willuhn.jameica.hbci.SynchronizeOptions;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJob;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.logging.Logger;

public class Psd2SynchronizeJobProvider implements Psd2JobProvider
{
    @Resource
    private Psd2SynchronizeBackend backend;

    @Override
    public List<SynchronizeJob> getSynchronizeJobs(Konto konto)
    {
        try
        {
            if (!supports(SynchronizeJobKontoauszug.class, konto))
                return List.of();
            SynchronizeOptions options = new SynchronizeOptions(konto);
            if (!options.getSyncKontoauszuege() && !options.getSyncSaldo())
                return List.of();
            Psd2SynchronizeJob job = backend.create(SynchronizeJobKontoauszug.class, konto);
            job.setContext(SynchronizeJob.CTX_ENTITY, konto);
            return List.of(job);
        }
        catch (Exception e)
        {
            Logger.error("PSD2-Synchronisierungsjob konnte nicht erzeugt werden", e);
            return List.of();
        }
    }

    @Override
    public List<Class<? extends SynchronizeJob>> getJobTypes()
    {
        return List.of(Psd2SynchronizeJob.class);
    }

    @Override
    public boolean supports(Class type, Konto konto)
    {
        try
        {
            return konto != null && Psd2SynchronizeBackend.class.getName().equals(konto.getBackendClass());
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @Override
    public int compareTo(Object other)
    {
        return 0;
    }
}
