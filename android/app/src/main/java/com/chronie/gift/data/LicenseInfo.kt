package com.chronie.gift.data

data class LicenseInfo(
    val name: String,
    val version: String,
    val license: String,
    val licenseText: String,
    val url: String
)

object LicensesData {
    val licenses = listOf(
        LicenseInfo(
            name = "AndroidX Core KTX",
            version = "1.17.0",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/core",
            licenseText = """
                Apache License
                Version 2.0, January 2004
                http://www.apache.org/licenses/

                TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

                1. Definitions.

                "License" shall mean the terms and conditions for use, reproduction,
                and distribution as defined by Sections 1 through 9 of this document.

                "Licensor" shall mean the copyright owner or entity authorized by
                the copyright owner that is granting the License.

                "Legal Entity" shall mean the union of the acting entity and all
                other entities that control, are controlled by, or are under common
                control with that entity.

                "You" (or "Your") shall mean an individual or Legal Entity
                exercising permissions granted by this License.

                "Source" form shall mean the preferred form for making modifications,
                including but not limited to software source code, documentation
                source, and configuration files.

                "Object" form shall mean any form resulting from mechanical
                transformation or translation of a Source form.

                "Work" shall mean the work of authorship made available under the
                License.

                "Derivative Works" shall mean any work that is based on the Work.

                "Contribution" shall mean any work of authorship submitted to the
                Licensor for inclusion in the Work.

                "Contributor" shall mean Licensor and any Legal Entity on behalf of
                whom a Contribution has been received by Licensor.

                2. Grant of Copyright License. Subject to the terms and conditions of
                this License, each Contributor hereby grants to You a perpetual,
                worldwide, non-exclusive, no-charge, royalty-free, irrevocable
                copyright license to reproduce, prepare Derivative Works of,
                publicly display, publicly perform, sublicense, and distribute the
                Work and such Derivative Works in Source or Object form.

                3. Grant of Patent License. Subject to the terms and conditions of
                this License, each Contributor hereby grants to You a perpetual,
                worldwide, non-exclusive, no-charge, royalty-free, irrevocable
                patent license to make, have made, use, offer to sell, sell, import,
                and otherwise transfer the Work.

                4. Redistribution. You may reproduce and distribute copies of the
                Work or Derivative Works thereof in any medium, with or without
                modifications, and in Source or Object form, provided that You
                meet the following conditions:

                (a) You must give any other recipients of the Work or
                    Derivative Works a copy of this License; and

                (b) You must cause any modified files to carry prominent notices
                    stating that You changed the files; and

                (c) You must retain, in the Source form of any Derivative Works
                    that You distribute, all copyright, patent, trademark, and
                    attribution notices from the Source form of the Work; and

                (d) If the Work includes a "NOTICE" text file, You must include
                    a readable copy of the attribution notices contained within
                    such NOTICE file.

                5. Submission of Contributions. Unless You explicitly state otherwise,
                any Contribution intentionally submitted for inclusion in the Work
                by You to the Licensor shall be under the terms and conditions of
                this License, without any additional terms or conditions.

                6. Trademarks. This License does not grant permission to use the trade
                names, trademarks, service marks, or product names of the Licensor.

                7. Disclaimer of Warranty. Unless required by applicable law or
                agreed to in writing, Licensor provides the Work on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.

                8. Limitation of Liability. In no event shall any Contributor be
                liable to You for damages, including any direct, indirect, special,
                incidental, or consequential damages.

                9. Accepting Warranty or Additional Liability. While redistributing
                the Work, You may choose to offer acceptance of support, warranty,
                indemnity, or other liability obligations consistent with this License.

                END OF TERMS AND CONDITIONS
            """.trimIndent()
        ),
        LicenseInfo(
            name = "AndroidX Lifecycle Runtime KTX",
            version = "2.10.0",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            licenseText = "Apache License 2.0 - See full license text above."
        ),
        LicenseInfo(
            name = "AndroidX Activity Compose",
            version = "1.12.3",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/activity",
            licenseText = "Apache License 2.0 - See full license text above."
        ),
        LicenseInfo(
            name = "AndroidX Compose BOM",
            version = "2026.01.01",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/compose",
            licenseText = "Apache License 2.0 - See full license text above."
        ),
        LicenseInfo(
            name = "AndroidX Navigation Compose",
            version = "2.9.7",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/navigation",
            licenseText = "Apache License 2.0 - See full license text above."
        ),
        LicenseInfo(
            name = "Ktor Client",
            version = "3.4.0",
            license = "Apache License 2.0",
            url = "https://github.com/ktorio/ktor",
            licenseText = """
                Copyright 2014-2024 JetBrains s.r.o and contributors.

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            """.trimIndent()
        ),
        LicenseInfo(
            name = "Kotlinx Serialization JSON",
            version = "1.8.0",
            license = "Apache License 2.0",
            url = "https://github.com/Kotlin/kotlinx.serialization",
            licenseText = """
                Copyright 2017-2024 JetBrains s.r.o.

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            """.trimIndent()
        ),
        LicenseInfo(
            name = "Coil",
            version = "3.3.0",
            license = "Apache License 2.0",
            url = "https://github.com/coil-kt/coil",
            licenseText = """
                Copyright 2024 Coil Contributors

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    https://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            """.trimIndent()
        ),
        LicenseInfo(
            name = "Miuix",
            version = "0.8.3",
            license = "Apache License 2.0",
            url = "https://github.com/miuix-kotlin-multiplatform/miuix",
            licenseText = """
                Copyright 2024-2025 Miuix Contributors

                Licensed under the Apache License, Version 2.0 (the "License");
                you may not use this file except in compliance with the License.
                You may obtain a copy of the License at

                    http://www.apache.org/licenses/LICENSE-2.0

                Unless required by applicable law or agreed to in writing, software
                distributed under the License is distributed on an "AS IS" BASIS,
                WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
                See the License for the specific language governing permissions and
                limitations under the License.
            """.trimIndent()
        ),
        LicenseInfo(
            name = "JUnit",
            version = "4.13.2",
            license = "Eclipse Public License 1.0",
            url = "https://junit.org/junit4/",
            licenseText = """
                Eclipse Public License - v 1.0

                THE ACCOMPANYING PROGRAM IS PROVIDED UNDER THE TERMS OF THIS ECLIPSE
                PUBLIC LICENSE ("AGREEMENT"). ANY USE, REPRODUCTION OR DISTRIBUTION OF
                THE PROGRAM CONSTITUTES RECIPIENT'S ACCEPTANCE OF THIS AGREEMENT.

                1. DEFINITIONS

                "Contribution" means:
                    a) in the case of the initial Contributor, the initial code and
                       documentation distributed under this Agreement, and
                    b) in the case of each subsequent Contributor:
                    i) changes to the Program, and
                    ii) additions to the Program;
                    where such changes and/or additions to the Program originate from
                    and are distributed by that particular Contributor.

                "Contributor" means each individual or entity that creates or contributes
                to the creation of Modifications.

                "Covered Code" means the Initial Code, Modifications, or combination
                of the Initial Code and Modifications.

                "Executable" means Covered Code in any form other than Source Code.

                "Initial Developer" means the individual or entity identified as the
                Initial Developer in the notice required by Exhibit A.

                "Larger Work" means a work which combines Covered Code or portions
                thereof with code not governed by the terms of this Agreement.

                "License" means this document.

                "Modifications" means any addition to or deletion from the substance
                or structure of either the Initial Code or any previous Modifications.

                "Original Code" means Source Code of computer software code which is
                described in the source code notice required by Exhibit A.

                "Patent Claims" means any patent claim(s), now owned or hereafter
                acquired, including without limitation, method, process, and apparatus
                claims.

                "Source Code" means the preferred form of the Covered Code for making
                modifications to it.

                "You" (or "Your") means an individual or a legal entity exercising
                rights under, and complying with all of the terms of, this Agreement.

                2. SOURCE CODE LICENSE

                a) Subject to the terms of this Agreement, each Contributor hereby
                grants You a world-wide, royalty-free, non-exclusive license, subject
                to third party intellectual property claims:
                    (i) to use, reproduce, modify, display, perform, sublicense and
                    distribute the Original Code (or portions thereof) with or without
                    Modifications, or as part of a Larger Work; and
                    (ii) under Patent Claims infringed by the making, using or selling
                    of Original Code, to make, have made, use, practice, sell, and
                    offer for sale, and/or otherwise dispose of the Original Code
                    (or portions thereof).

                b) Subject to the terms of this Agreement, each Contributor hereby
                grants You a world-wide, royalty-free, non-exclusive license, subject
                to third party intellectual property claims, to make, have made, use,
                practice, sell, offer for sale, import, and otherwise transfer the
                Modifications.

                3. DISTRIBUTION OBLIGATIONS

                a) Application of License. The Modifications which You create or to
                which You contribute are governed by the terms of this Agreement.

                b) Availability of Source Code. Any Modification which You create or
                to which You contribute must be made available in Source Code form.

                c) Description of Modifications. You must cause all Covered Code to
                which You contribute to contain a file documenting the changes You
                made.

                d) Intellectual Property Matters.
                    (i) Third Party Claims. If Contributor has knowledge that a license
                    under a third party's intellectual property rights is required,
                    Contributor must include a text file with the distribution.

                e) Required Notices. You must duplicate the notice in Exhibit A in
                each file of the Source Code.

                4. COMMERCIAL DISTRIBUTION

                Commercial distributors of software may accept certain responsibilities
                with respect to end users, business partners and the like.

                5. NO WARRANTY

                EXCEPT AS EXPRESSLY SET FORTH IN THIS AGREEMENT, THE PROGRAM IS
                PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
                KIND.

                6. DISCLAIMER OF LIABILITY

                EXCEPT AS EXPRESSLY SET FORTH IN THIS AGREEMENT, NEITHER RECIPIENT
                NOR ANY CONTRIBUTORS SHALL HAVE ANY LIABILITY FOR ANY DIRECT, INDIRECT,
                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

                7. GENERAL

                If any provision of this Agreement is invalid or unenforceable under
                applicable law, it shall not affect the validity or enforceability of
                the remainder of the terms of this Agreement.
            """.trimIndent()
        ),
        LicenseInfo(
            name = "AndroidX Test Ext JUnit",
            version = "1.3.0",
            license = "Apache License 2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/test",
            licenseText = "Apache License 2.0 - See full license text above."
        ),
        LicenseInfo(
            name = "Espresso Core",
            version = "3.7.0",
            license = "Apache License 2.0",
            url = "https://developer.android.com/training/testing/espresso",
            licenseText = "Apache License 2.0 - See full license text above."
        ),
        LicenseInfo(
            name = "Latin Modern Math Font",
            version = "1.0",
            license = "GUST Font License",
            url = "https://www.gust.org.pl/projects/e-foundry/lm-math",
            licenseText = """
                Copyright (c) 2003, 2011-2014 by the GUST e-foundry.
                (http://www.gust.org.pl/projects/e-foundry)

                This work is released under the GUST Font License.

                The GUST Font License (GFL) is a free license that permits redistribution
                and modification of the fonts. The full text of the license is available at:
                http://www.gust.org.pl/projects/e-foundry/licenses/GUST-FONT-LICENSE.txt

                The fonts may be used for any purpose, including commercial use, and may be
                modified and redistributed, provided that the license terms are followed.

                THE FONTS ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
            """.trimIndent()
        )
    )
}
