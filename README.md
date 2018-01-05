Leo-III 1.2
========
*An automated theorem prover for classical higher-order logic (with choice)*

Leo-III [SWB16] is an automated theorem prover for (polymorphic) higher-order logic which supports all common TPTP dialects, including THF, TFF and FOF as well as their rank-1 polymorphic derivatives [SWB17]. 
It is based on a paramodulation calculus with ordering constraints and, in tradition of its predecessor LEO-II [BP15], heavily relies on cooperation with external (mostly first-order) theorem provers for increased performance. Nevertheless, Leo-III can also be used as a stand-alone prover without employing any external cooperation.

Leo-III is developed at Freie Universität Berlin as part of the German National Research Foundation (DFG) project BE 2501/11-1. The main contributors are (sorted alphabetically): Christoph Benzmüller, Tomer Libal, Alexander Steen and Max Wisniewski. For a full list of contributors to the project and used and third-party libraries, please refer to the `AUTHORS` file in the source distribution.


## Install
See [INSTALL.md](INSTALL.md)

## Usage
See [USAGE.md](USAGE.md)


## Further information
Leo-III is licenced under the BSD 3-clause "New" or "Revised" License, see `LICENCE` in the source distribution.

Further information including related projects, current publications etc, can be found on the [Leo-III web site](http://www.inf.fu-berlin.de/~lex/leo3), and for details on the Leo-III system (implementation), we refer to the system description [BSW17].

## Contributing to the project

We are always greateful to hear feedback from our users:

- If you are using Leo-III for any project yourself, we would be happy to hear about it! 
- If you encounter problems using Leo-III, feel tree to open a bug report (or simply a question) on the GitHub page.
- If you are interested to contribute to the project, simply fork the GitHub repository and open pull requests!

## References

[BP15] 	Christoph Benzmüller, Lawrence C. Paulson, Nik Sultana, Frank Theiß, The Higher-Order Prover LEO-II, In Journal of Automated Reasoning, volume 55, number 4, pp. 389-404, 2015.

[BSW17] Christoph Benzmüller, Alexander Steen, Max Wisniewski Leo-III Version 1.1 (System description), In Thomas Eiter, David Sands, Geoff Sutcliffe and Andrei Voronkov (Eds.), IWIL Workshop and LPAR Short Presentations, EasyChair, Kalpa Publications in Computing, Volume 1, pp. 11-26, 2017.

[GSB17] Tobias Gleißner, Alexander Steen, Christoph Benzmüller, Theorem Provers for Every Normal Modal Logic. In LPAR-21. 21st International Conference on Logic for Programming, Artificial Intelligence and Reasoning (Thomas Eiter, David Sands, eds.), EasyChair, EPiC Series in Computing, volume 46, pp. 14-30, 2017.

[SWB16] Alexander Steen, Max Wisniewski, Christoph Benzmüller, Agent-Based HOL Reasoning. In 5th International Congress on Mathematical Software, ICMS 2016, Berlin, Germany, July 2016, Proceedings, Springer, LNCS, volume 9725. 2016.

[SWB17] Alexander Steen, Max Wisniewski, Christoph Benzmüller, Going Polymorphic - TH1 Reasoning for Leo-III. In IWIL@LPAR 2017 Workshop and LPAR-21 Short Presentations, Maun, Botswana, May 7-12, 2017 (Thomas Eiter, David Sands, Geoff Sutcliffe, Andrei Voronkov, eds.), EasyChair, Kalpa Publications in Computing, volume 1, 2017.

[Sut08] Sutcliffe G. (2008), The SZS Ontologies for Automated Reasoning Software, 
    Rudnicki P., Sutcliffe G., Proceedings of the LPAR Workshops: Knowledge 
    Exchange: Automated Provers and Proof Assistants, and The 7th International 
    Workshop on the Implementation of Logics (Doha, Qattar), CEUR Workshop 
    Proceedings 418, 38-49.
    

