package org.benf.cfr.reader;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.state.ClassFileSourceChained;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.ClassFileSourceWrapper;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.DumperFactory;
import org.benf.cfr.reader.util.output.InternalDumperFactoryImpl;
import org.benf.cfr.reader.util.output.SinkDumperFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class CfrDriverImpl implements CfrDriver {
    private final Options options;
    private final ClassFileSource2 classFileSource;
    private final OutputSinkFactory outputSinkFactory;

    public CfrDriverImpl(ClassFileSource source, OutputSinkFactory outputSinkFactory, Options options, boolean fallbackToDefaultSource) {
        if (options == null) {
            options = new OptionsImpl(new HashMap<String, String>());
        }
        ClassFileSource2 tmpSource;
        if (source == null) {
            tmpSource = new ClassFileSourceImpl(options);
        } else {
            tmpSource = source instanceof ClassFileSource2 ? (ClassFileSource2)source : new ClassFileSourceWrapper(source);
            if (fallbackToDefaultSource) {
                tmpSource = new ClassFileSourceChained(Arrays.asList(tmpSource, new ClassFileSourceImpl(options)));
            }
        }
        this.outputSinkFactory = outputSinkFactory;
        this.options = options;
        this.classFileSource = tmpSource;
    }

    @Override
    public DCCommonState analyse(String toAnalyse) {

        // TODO : We shouldn't have to discard state here.  But we do, because
        // it causes test fails.  (used class name table retains useful symbols).
        classFileSource.informAnalysisRelativePathDetail(null, null);
        // Note - both of these need to be reset, as they have caches.
        DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
        DumperFactory dumperFactory = outputSinkFactory != null ?
                new SinkDumperFactory(outputSinkFactory, options) :
                new InternalDumperFactoryImpl(options);

        Driver.doJar(dcCommonState, toAnalyse, AnalysisType.JAR, dumperFactory);

        return dcCommonState;
    }
}
