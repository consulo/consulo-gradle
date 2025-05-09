/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.project.data;

import consulo.container.boot.ContainerPathManager;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.rt.model.ExternalProject;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;

/**
 * @author Vladislav.Soroka
 * @since 2014-07-15
 */
public class ExternalProjectSerializer {
    private static final Logger LOG = Logger.getInstance(ExternalProjectSerializer.class);

//    private final Kryo myKryo;

    public ExternalProjectSerializer() {
//        myKryo = new Kryo() {
//            @Override
//            public <T> T newInstance(Class<T> type) {
//                LOG.error("Serializing default type: " + type);
//                return super.newInstance(type);
//            }
//        };
//        configureKryo();
    }
//
//    private void configureKryo() {
//        myKryo.setAutoReset(true);
//
//        myKryo.setRegistrationRequired(true);
//        Log.set(Log.LEVEL_WARN);
//
//        myKryo.register(ArrayList.class, new CollectionSerializer() {
//            @Override
//            protected Collection create(Kryo kryo, Input input, Class<Collection> type) {
//                return new ArrayList();
//            }
//        });
//        myKryo.register(HashMap.class, new MapSerializer() {
//            @Override
//            protected Map create(Kryo kryo, Input input, Class<Map> type) {
//                return new HashMap();
//            }
//        });
//        myKryo.register(HashSet.class, new CollectionSerializer() {
//            @Override
//            protected Collection create(Kryo kryo, Input input, Class<Collection> type) {
//                return new HashSet();
//            }
//        });
//
//        myKryo.register(File.class, new FileSerializer());
//        myKryo.register(DefaultExternalProject.class, new FieldSerializer<DefaultExternalProject>(myKryo, DefaultExternalProject.class) {
//            @Override
//            protected DefaultExternalProject create(Kryo kryo, Input input, Class<DefaultExternalProject> type) {
//                return new DefaultExternalProject();
//            }
//        });
//
//        myKryo.register(DefaultExternalTask.class, new FieldSerializer<DefaultExternalTask>(myKryo, DefaultExternalTask.class) {
//            @Override
//            protected DefaultExternalTask create(Kryo kryo, Input input, Class<DefaultExternalTask> type) {
//                return new DefaultExternalTask();
//            }
//        });
//
//        myKryo.register(DefaultExternalPlugin.class, new FieldSerializer<DefaultExternalPlugin>(myKryo, DefaultExternalPlugin.class) {
//            @Override
//            protected DefaultExternalPlugin create(Kryo kryo, Input input, Class<DefaultExternalPlugin> type) {
//                return new DefaultExternalPlugin();
//            }
//        });
//
//        myKryo.register(
//            DefaultExternalSourceSet.class,
//            new FieldSerializer<DefaultExternalSourceSet>(myKryo, DefaultExternalSourceSet.class) {
//                @Override
//                protected DefaultExternalSourceSet create(Kryo kryo, Input input, Class<DefaultExternalSourceSet> type) {
//                    return new DefaultExternalSourceSet();
//                }
//            }
//        );
//
//        myKryo.register(
//            DefaultExternalSourceDirectorySet.class,
//            new FieldSerializer<DefaultExternalSourceDirectorySet>(myKryo, DefaultExternalSourceDirectorySet.class) {
//                @Override
//                protected DefaultExternalSourceDirectorySet create(Kryo kryo, Input input, Class<DefaultExternalSourceDirectorySet> type) {
//                    return new DefaultExternalSourceDirectorySet();
//                }
//            }
//        );
//
//        myKryo.register(DefaultExternalFilter.class, new FieldSerializer<DefaultExternalFilter>(myKryo, DefaultExternalFilter.class) {
//            @Override
//            protected DefaultExternalFilter create(Kryo kryo, Input input, Class<DefaultExternalFilter> type) {
//                return new DefaultExternalFilter();
//            }
//        });
//
//        myKryo.register(ExternalSystemSourceType.class, new DefaultSerializers.EnumSerializer(ExternalSystemSourceType.class));
//
//        myKryo.register(LinkedHashSet.class, new CollectionSerializer() {
//            @Override
//            protected Collection create(Kryo kryo, Input input, Class<Collection> type) {
//                return new LinkedHashSet();
//            }
//        });
//        myKryo.register(HashSet.class, new CollectionSerializer() {
//            @Override
//            protected Collection create(Kryo kryo, Input input, Class<Collection> type) {
//                return new HashSet();
//            }
//        });
//        myKryo.register(Set.class, new CollectionSerializer() {
//            @Override
//            protected Collection create(Kryo kryo, Input input, Class<Collection> type) {
//                return new HashSet();
//            }
//        });
//    }


    public void save(@Nonnull ExternalProject externalProject) {
//        Output output = null;
//        try {
//            final String externalProjectPath = externalProject.getProjectDir().getPath();
//            final File configurationFile =
//                getProjectConfigurationFile(new ProjectSystemId(externalProject.getExternalSystemId()), externalProjectPath);
//            if (!FileUtil.createParentDirs(configurationFile)) {
//                return;
//            }
//
//            output = new Output(new FileOutputStream(configurationFile));
//            myKryo.writeObject(output, externalProject);
//        }
//        catch (FileNotFoundException e) {
//            LOG.error(e);
//        }
//        finally {
//            StreamUtil.closeStream(output);
//        }
    }

    @Nullable
    public ExternalProject load(@Nonnull ProjectSystemId externalSystemId, File externalProjectPath) {
//        Input input = null;
//        try {
//            final File configurationFile = getProjectConfigurationFile(externalSystemId, externalProjectPath.getPath());
//            if (!configurationFile.isFile()) {
//                return null;
//            }
//
//            input = new Input(new FileInputStream(configurationFile));
//            return myKryo.readObject(input, DefaultExternalProject.class);
//        }
//        catch (Exception e) {
//            LOG.error(e);
//        }
//        finally {
//            StreamUtil.closeStream(input);
//        }

        return null;
    }

    private static File getProjectConfigurationFile(ProjectSystemId externalSystemId, String externalProjectPath) {
        return new File(
            getProjectConfigurationDir(externalSystemId),
            Integer.toHexString(externalProjectPath.hashCode()) + "/project.dat"
        );
    }

    private static File getProjectConfigurationDir(ProjectSystemId externalSystemId) {
        return getPluginSystemDir(externalSystemId, "Projects");
    }

    private static File getPluginSystemDir(ProjectSystemId externalSystemId, String folder) {
        return new File(
            ContainerPathManager.get().getSystemPath(),
            externalSystemId.getId().toLowerCase() + "/" + folder
        ).getAbsoluteFile();
    }

//    private static class FileSerializer extends Serializer<File> {
//        private final Kryo myStdKryo;
//
//        public FileSerializer() {
//            myStdKryo = new Kryo();
//            myStdKryo.register(File.class);
//            myStdKryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
//        }
//
//        @Override
//        public void write(Kryo kryo, Output output, File object) {
//            myStdKryo.writeObject(output, object);
//        }
//
//        @Override
//        public File read(Kryo kryo, Input input, Class<File> type) {
//            File file = myStdKryo.readObject(input, File.class);
//            return new File(file.getPath());
//        }
//    }

//  private static class StdSerializer<T> extends Serializer<T> {
//    private final Kryo myStdKryo;
//
//    public StdSerializer(Class<T> clazz) {
//      myStdKryo = new Kryo();
//      myStdKryo.register(clazz);
//      myStdKryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
//    }
//
//    @Override
//    public void write(Kryo kryo, Output output, T object) {
//      myStdKryo.writeObject(output, object);
//    }
//
//    @Override
//    public T read(Kryo kryo, Input input, Class<T> type) {
//      return myStdKryo.readObject(input, type);
//    }
//  }
}
