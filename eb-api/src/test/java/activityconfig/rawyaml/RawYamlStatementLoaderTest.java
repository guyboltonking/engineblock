/*
 *
 *    Copyright 2016 jshook
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * /
 */

package activityconfig.rawyaml;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class RawYamlStatementLoaderTest {

    @Test
    public void tetLoadPropertiesBlock() {
        RawYamlStatementLoader ysl = new RawYamlStatementLoader();
        RawStmtsDocList rawBlockDocs = ysl.load("testdocs/rawblock.yaml");
        assertThat(rawBlockDocs.getStmtsDocs()).hasSize(1);
        RawStmtsDoc rawBlockDoc = rawBlockDocs.getStmtsDocs().get(0);
        assertThat(rawBlockDoc.getStatements()).hasSize(1);
        assertThat(rawBlockDoc.getBindings()).hasSize(1);
        assertThat(rawBlockDoc.getName()).isEqualTo("name");
        assertThat(rawBlockDoc.getTags()).hasSize(1);
        assertThat(rawBlockDoc.getParams()).hasSize(1);
    }

    @Test
    public void testLoadFullFormat() {
        RawYamlStatementLoader ysl = new RawYamlStatementLoader();
        RawStmtsDocList erthing = ysl.load("testdocs/everything.yaml");
        List<RawStmtsDoc> rawStmtsDocs = erthing.getStmtsDocs();
        assertThat(rawStmtsDocs).hasSize(2);
        RawStmtsDoc rawStmtsDoc = rawStmtsDocs.get(0);
        List<RawStmtsBlock> blocks = rawStmtsDoc.getBlocks();
        assertThat(rawStmtsDoc.getName()).isEqualTo("doc1");
        assertThat(blocks).hasSize(1);
        RawStmtsBlock rawStmtsBlock = blocks.get(0);
        assertThat(rawStmtsBlock.getName()).isEqualTo("block0");
    }


}
